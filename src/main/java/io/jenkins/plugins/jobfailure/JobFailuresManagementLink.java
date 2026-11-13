package io.jenkins.plugins.jobfailure;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.scheduler.CronTabList;
import hudson.scheduler.Hash;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

/** Dedicated Manage Jenkins page for Failure Radar settings. */
@Extension
public final class JobFailuresManagementLink extends ManagementLink {
    @Override
    public String getIconFileName() {
        return "symbol-failure-radar plugin-failure-radar";
    }

    @Override
    public String getDisplayName() {
        return JobFailuresI18n.text("plugin.name");
    }

    @Override
    public String getUrlName() {
        return "failure-radar-settings";
    }

    @Override
    public String getDescription() {
        return JobFailuresI18n.text("management.description");
    }

    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @Override
    public hudson.security.Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    public JobFailuresConfiguration getConfiguration() {
        return JobFailuresConfiguration.get();
    }

    public int getRecentBuildCount() {
        return getConfiguration().getRecentBuildCount();
    }

    public boolean isEmailNotificationsEnabled() {
        return getConfiguration().isEmailNotificationsEnabled();
    }

    public String getEmailRecipients() {
        return getConfiguration().getEmailRecipients();
    }

    public String getEmailSchedule() {
        return getConfiguration().getEmailSchedule();
    }

    @GET
    public void doPreviewSchedule(@QueryParameter String schedule, StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String expression = schedule == null || schedule.isBlank() ? "H 8 * * *" : schedule.trim();
        rsp.setContentType("text/plain;charset=UTF-8");
        try {
            CronTabList cron = CronTabList.create(expression, Hash.from("failure-radar-email"));
            Calendar candidate = Calendar.getInstance();
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            candidate.add(Calendar.MINUTE, 1);

            // Check at minute boundaries for the next two years, using Jenkins' cron parser and controller timezone.
            int minutesToSearch = 2 * 366 * 24 * 60;
            for (int i = 0; i < minutesToSearch; i++) {
                if (cron.check(candidate)) {
                    Locale locale = JobFailuresI18n.supportedLocale(req.getLocale());
                    String formatted =
                            new SimpleDateFormat("EEE, MMM d, yyyy HH:mm z", locale).format(candidate.getTime());
                    rsp.getWriter().print(JobFailuresI18n.text(locale, "schedule.next", formatted));
                    return;
                }
                candidate.add(Calendar.MINUTE, 1);
            }
            rsp.setStatus(400);
            rsp.getWriter().print(JobFailuresI18n.text(req.getLocale(), "schedule.noMatch"));
        } catch (IllegalArgumentException e) {
            rsp.setStatus(400);
            rsp.getWriter().print(JobFailuresI18n.text(req.getLocale(), "schedule.invalid"));
        }
    }

    @POST
    public void doSendTestEmail(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String recipients = req.getParameter("emailRecipients");
        if (recipients == null || recipients.isBlank()) {
            rsp.sendRedirect2(req.getContextPath() + "/manage/" + getUrlName() + "/?emailTest=missingRecipients");
            return;
        }

        int count = getConfiguration().getRecentBuildCount();
        String submittedCount = req.getParameter("recentBuildCount");
        if (submittedCount != null && !submittedCount.isBlank()) {
            try {
                count = Integer.parseInt(submittedCount);
            } catch (NumberFormatException e) {
                rsp.sendError(400, JobFailuresI18n.text(req.getLocale(), "form.count.invalid"));
                return;
            }
        }
        if (count < 1) {
            rsp.sendError(400, JobFailuresI18n.text(req.getLocale(), "form.count.minimum"));
            return;
        }

        try {
            ExtensionList.lookupSingleton(JobFailuresEmailNotifier.class).sendTestEmail(recipients.trim(), count);
            rsp.sendRedirect2(req.getContextPath() + "/manage/" + getUrlName() + "/?emailTest=sent");
        } catch (Exception e) {
            rsp.sendRedirect2(req.getContextPath() + "/manage/" + getUrlName() + "/?emailTest=failed");
        }
    }

    @POST
    public void doSave(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String submittedCount = req.getParameter("recentBuildCount");
        int count;
        try {
            count = Integer.parseInt(submittedCount);
        } catch (NumberFormatException e) {
            rsp.sendError(400, JobFailuresI18n.text(req.getLocale(), "form.count.invalid"));
            return;
        }
        if (count < 1) {
            rsp.sendError(400, JobFailuresI18n.text(req.getLocale(), "form.count.minimum"));
            return;
        }

        JobFailuresConfiguration configuration = getConfiguration();
        boolean emailNotificationsEnabled = "true".equals(req.getParameter("emailNotificationsEnabled"));
        String schedule = req.getParameter("emailSchedule");
        if (emailNotificationsEnabled) {
            try {
                hudson.scheduler.CronTabList.create(
                        schedule == null || schedule.isBlank() ? "H 8 * * *" : schedule.trim(),
                        hudson.scheduler.Hash.from("failure-radar-email"));
            } catch (IllegalArgumentException e) {
                rsp.sendError(400, "Enter a valid Jenkins cron expression.");
                return;
            }
        }

        configuration.setRecentBuildCount(count);
        configuration.setEmailNotificationsEnabled(emailNotificationsEnabled);
        if (emailNotificationsEnabled) {
            configuration.setEmailRecipients(req.getParameter("emailRecipients"));
            configuration.setEmailSchedule(schedule);
        }
        rsp.sendRedirect2(req.getContextPath() + "/manage/" + getUrlName() + "/");
    }
}
