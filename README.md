# Failure Radar

Failure Radar is a Jenkins plugin for tracking selected jobs and spotting repeated build failures in one place. It works with Pipeline and Freestyle jobs and can send scheduled email summaries.

## Features

- Opt individual jobs into failure tracking; jobs are included regardless of whether they are started manually or by a schedule.
- Review recent build results for every tracked job on the Failure Radar page.
- Group jobs whose latest configured number of builds all finished with `FAILURE` separately from other tracked jobs.
- Send scheduled email summaries for jobs that meet the repeated-failure condition.
- Send a test email from the settings page without waiting for the next scheduled run.

## Requirements

- The Jenkins Mailer plugin and outgoing email settings configured in Jenkins to use email notifications. Mailer is installed as a plugin dependency.

The plugin's interface is available in English and Turkish. Other interface languages fall back to English. Notification emails are always sent in English.

## Installation

After Failure Radar is released to the Jenkins Update Center, install it from **Manage Jenkins → Plugins → Available plugins** by searching for **Failure Radar**.

To build and try the plugin before its Update Center release, create the HPI package as described in [Build locally](#build-locally), then install that package on a test Jenkins controller using the Jenkins Plugin Manager.

## Configure a job

1. Open a Pipeline or Freestyle job's configuration page.
2. Enable **Track failures on the Failure Radar page**.
3. Save the job configuration.

Tracked jobs appear on the **Failure Radar** page. The overview uses one shared build count for all tracked jobs. A job is placed in the repeated-failures group only when it has that many recent builds and every one has the result `FAILURE`. Jobs that do not meet this condition appear in the other tracked-jobs group.

## Configure Failure Radar

Open **Manage Jenkins → Failure Radar** to configure the overview and email notifications.

### Recent build count

Set how many recent builds to display and how many consecutive failed builds are required for the repeated-failures group. The default is `5`; the minimum is `1`.

### Email notifications

Email notifications are disabled by default. To enable them:

1. Enable **Email notifications**.
2. Enter one or more recipient addresses, separated by commas.
3. Set the schedule using Jenkins cron syntax.
4. Save the settings.

The default schedule is `H 8 * * *`, which runs once daily at a consistent hashed minute during the 08:00 hour in the Jenkins controller's time zone. The next scheduled send time is shown below the schedule field.

An email is sent only when at least one tracked job meets the repeated-failure condition at the scheduled time. No scheduled email is sent when there are no matching jobs. Use **Send test email now** to check email delivery immediately; the test message is marked as a test and is sent even when there are no matching failures.

Failure Radar uses the Jenkins Mailer configuration to send email. Configure the SMTP server and sender address in Jenkins before enabling notifications.

## Build locally

Run a development Jenkins controller with the plugin:

```bash
mvn hpi:run
```

Build the distributable HPI package:

```bash
mvn clean package
```

The package is written to `target/failure-radar.hpi`.

## License

Failure Radar is licensed under the MIT License. See [LICENSE.md](LICENSE.md).
