package io.jenkins.plugins.jobfailure;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scheduler.CronTabList;
import hudson.scheduler.Hash;
import hudson.tasks.Mailer;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends scheduled email summaries for tracked jobs with consecutive recent failures. */
@Extension
public final class JobFailuresEmailNotifier extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(JobFailuresEmailNotifier.class.getName());
    private static final long CHECK_INTERVAL_MILLIS = 60_000L;

    public JobFailuresEmailNotifier() {
        super("Failure Radar email notifications");
    }

    @Override
    public long getRecurrencePeriod() {
        return CHECK_INTERVAL_MILLIS;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        JobFailuresConfiguration configuration = JobFailuresConfiguration.get();
        if (!configuration.isEmailNotificationsEnabled()) {
            return;
        }

        String recipients = configuration.getEmailRecipients().trim();
        if (recipients.isEmpty()) {
            listener.getLogger().println("Failure Radar email notifications are enabled, but no recipients are configured.");
            return;
        }

        Calendar now = Calendar.getInstance();
        long currentMinute = now.getTimeInMillis() / CHECK_INTERVAL_MILLIS;
        CronTabList schedule;
        try {
            schedule = CronTabList.create(configuration.getEmailSchedule(), Hash.from("failure-radar-email"));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Invalid Failure Radar email schedule: " + configuration.getEmailSchedule(), e);
            return;
        }

        if (!schedule.check(now) || configuration.getLastEmailNotificationMinute() == currentMinute) {
            return;
        }

        List<JobFailure> failures = findRepeatedFailures(configuration.getRecentBuildCount());
        if (failures.isEmpty()) {
            return;
        }

        // Persist the occurrence before sending so a controller restart cannot duplicate the same scheduled email.
        configuration.setLastEmailNotificationMinute(currentMinute);
        try {
            sendEmail(recipients, configuration.getRecentBuildCount(), failures, false);
            listener.getLogger().println("Sent Failure Radar summary to " + recipients + " (" + failures.size() + " jobs).");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send the scheduled Failure Radar email.", e);
            listener.getLogger().println("Failed to send the scheduled Failure Radar email: " + e.getMessage());
        }
    }

    private List<JobFailure> findRepeatedFailures(int count) {
        List<JobFailure> failures = new ArrayList<>();
        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            FailureTrackingProperty property = job.getProperty(FailureTrackingProperty.class);
            if (property == null || !property.isEnabled()) {
                continue;
            }

            List<Run<?, ?>> builds = new ArrayList<>();
            for (Run<?, ?> run : job.getBuilds().limit(count)) {
                builds.add(run);
            }
            if (builds.size() != count || builds.stream().anyMatch(run -> run.isBuilding() || run.getResult() != Result.FAILURE)) {
                continue;
            }
            failures.add(new JobFailure(job, builds));
        }
        return failures;
    }

    public void sendTestEmail(String recipients, int count) throws Exception {
        try {
            sendEmail(recipients, count, findRepeatedFailures(count), true);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send the Failure Radar test email.", e);
            throw e;
        }
    }

    private void sendEmail(String recipients, int count, List<JobFailure> failures, boolean test) throws Exception {
        Locale locale = Locale.ENGLISH;
        Session session = Mailer.descriptor().createSession();
        MimeMessage message = new MimeMessage(session);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients.replace(';', ','), true));

        String adminAddress = JenkinsLocationConfiguration.get().getAdminAddress();
        if (adminAddress != null && !adminAddress.isBlank()) {
            message.setFrom(new InternetAddress(adminAddress.trim()));
        }
        String subject = JobFailuresI18n.text(locale, "email.subject", failures.size(), count);
        message.setSubject((test ? JobFailuresI18n.text(locale, "email.testPrefix") + " " : "") + subject, "UTF-8");
        message.setSentDate(new Date());
        MimeMultipart alternatives = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(createBody(count, failures, test, locale), "UTF-8");
        alternatives.addBodyPart(textPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(createHtmlBody(count, failures, test, locale), "text/html; charset=UTF-8");
        alternatives.addBodyPart(htmlPart);
        message.setContent(alternatives);
        Transport.send(message);
    }

    private String createBody(int count, List<JobFailure> failures, boolean test, Locale locale) {
        StringBuilder body = new StringBuilder();
        if (test) {
            body.append(JobFailuresI18n.text(locale, "email.testBody")).append("\n\n");
        }
        if (failures.isEmpty()) {
            body.append(JobFailuresI18n.text(locale, "email.noFailures", count)).append("\n\n");
        } else {
            body.append(JobFailuresI18n.text(locale, "email.hasFailures", count)).append("\n\n");
        }

        String rootUrl = Jenkins.get().getRootUrl();
        for (JobFailure failure : failures) {
            body.append("- ").append(failure.job.getFullDisplayName()).append('\n');
            for (Run<?, ?> run : failure.builds) {
                body.append("  Build #").append(run.getNumber()).append(": ")
                        .append(rootUrl == null ? run.getUrl() : rootUrl + run.getUrl()).append('\n');
            }
            body.append('\n');
        }
        body.append(JobFailuresI18n.text(locale, "email.generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm z", locale).format(new Date()))).append("\n");
        body.append(JobFailuresI18n.text(locale, "email.overview")).append(": ").append(getJenkinsUrl("failure-radar/")).append("\n");
        return body.toString();
    }

    private String createHtmlBody(int count, List<JobFailure> failures, boolean test, Locale locale) {
        StringBuilder html = new StringBuilder();
        String overviewUrl = getJenkinsUrl("failure-radar/");
        String generatedAt = new SimpleDateFormat("dd MMM yyyy, HH:mm z", locale)
                .format(new Date());

        html.append("<!doctype html><html><body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f4f6;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:100%;max-width:640px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;\">")
                .append("<tr><td style=\"background:#172554;padding:24px 28px;color:#ffffff;\">")
                .append("<div style=\"font-size:12px;letter-spacing:1.5px;font-weight:bold;color:#bfdbfe;\">FAILURE RADAR</div>")
                .append("<h1 style=\"margin:8px 0 0;font-size:24px;line-height:1.3;color:#ffffff;\">").append(escapeHtml(JobFailuresI18n.text(locale, "email.title"))).append("</h1>");
        if (test) {
            html.append("<div style=\"display:inline-block;margin-top:12px;padding:5px 10px;border-radius:999px;background:#fef3c7;color:#92400e;font-size:12px;font-weight:bold;\">").append(escapeHtml(JobFailuresI18n.text(locale, "email.testBadge"))).append("</div>");
        }
        html.append("</td></tr><tr><td style=\"padding:24px 28px;\">")
                .append("<p style=\"margin:0 0 18px;font-size:15px;line-height:1.6;\">")
                .append(escapeHtml(JobFailuresI18n.text(locale, "email.intro", count))).append("</p>")
                .append("<div style=\"margin:0 0 22px;padding:14px 16px;background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;color:#9a3412;font-size:14px;\">")
                .append("<strong style=\"font-size:20px;margin-right:8px;\">")
                .append(failures.size())
                .append("</strong> ").append(escapeHtml(JobFailuresI18n.text(locale, "email.summaryText"))).append("</div>");

        if (failures.isEmpty()) {
            html.append("<div style=\"padding:16px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;color:#4b5563;\">")
                    .append(escapeHtml(JobFailuresI18n.text(locale, "email.empty", count))).append("</div>");
        } else {
            for (JobFailure failure : failures) {
                String jobUrl = getJenkinsUrl(failure.job.getUrl());
                html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:0 0 16px;border:1px solid #e5e7eb;border-radius:8px;\">")
                        .append("<tr><td style=\"padding:16px 18px 10px;\">")
                        .append("<a href=\"").append(escapeHtml(jobUrl)).append("\" style=\"font-size:16px;font-weight:bold;color:#1d4ed8;text-decoration:none;\">")
                        .append(escapeHtml(failure.job.getFullDisplayName())).append("</a>")
                        .append("<div style=\"margin-top:5px;font-size:13px;color:#6b7280;\">")
                        .append(escapeHtml(JobFailuresI18n.text(locale, "email.jobSummary", count))).append("</div>")
                        .append("</td></tr><tr><td style=\"padding:0 18px 16px;\"><table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\"><tr>");
                for (Run<?, ?> run : failure.builds) {
                    String buildUrl = getJenkinsUrl(run.getUrl());
                    html.append("<td style=\"padding:0 8px 0 0;\">")
                            .append("<a href=\"").append(escapeHtml(buildUrl)).append("\" style=\"display:inline-block;padding:6px 9px;border-radius:6px;background:#fee2e2;color:#991b1b;font-size:12px;font-weight:bold;text-decoration:none;white-space:nowrap;\">#")
                            .append(run.getNumber()).append(" ").append(escapeHtml(JobFailuresI18n.text(locale, "email.failureBadge"))).append("</a></td>");
                }
                html.append("</tr></table></td></tr></table>");
            }
        }

        html.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:24px auto 8px;\"><tr><td align=\"center\" bgcolor=\"#1d4ed8\" style=\"border-radius:7px;\">")
                .append("<a href=\"").append(escapeHtml(overviewUrl))
                .append("\" style=\"display:inline-block;padding:13px 20px;color:#ffffff;font-size:14px;font-weight:bold;text-decoration:none;border-radius:7px;\">").append(escapeHtml(JobFailuresI18n.text(locale, "email.overview"))).append("</a>")
                .append("</td></tr></table>")
                .append("<p style=\"margin:18px 0 0;text-align:center;color:#6b7280;font-size:12px;\">").append(escapeHtml(JobFailuresI18n.text(locale, "email.reportTime", generatedAt)))
                .append("</p></td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String getJenkinsUrl(String path) {
        String rootUrl = Jenkins.get().getRootUrl();
        String relativePath = path == null ? "" : path;
        if (rootUrl == null || rootUrl.isBlank()) {
            return relativePath;
        }
        return (rootUrl.endsWith("/") ? rootUrl : rootUrl + "/") + relativePath;
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final class JobFailure {
        private final Job<?, ?> job;
        private final List<Run<?, ?>> builds;

        private JobFailure(Job<?, ?> job, List<Run<?, ?>> builds) {
            this.job = job;
            this.builds = builds;
        }
    }
}
