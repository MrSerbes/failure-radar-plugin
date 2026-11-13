package io.jenkins.plugins.jobfailure;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.RootAction;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

/** Jenkins page grouping opted-in jobs by their configured recent build count. */
@Extension
public final class JobFailuresRootAction implements RootAction {
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
        return "failure-radar";
    }

    public List<? extends Job<?, ?>> getTrackedJobs() {
        Jenkins.get().checkPermission(Jenkins.READ);
        return Jenkins.get().getAllItems(Job.class).stream()
                .filter(job -> {
                    FailureTrackingProperty property =
                            (FailureTrackingProperty) job.getProperty(FailureTrackingProperty.class);
                    return property != null && property.isEnabled();
                })
                .map(job -> (Job<?, ?>) job)
                .toList();
    }

    public List<JobGroup> getJobGroups() {
        int recentBuildCount = JobFailuresConfiguration.get().getRecentBuildCount();
        List<JobInfo> repeatedFailures = new ArrayList<>();
        List<JobInfo> otherJobs = new ArrayList<>();

        for (Object item : getTrackedJobs()) {
            Job<?, ?> job = (Job<?, ?>) item;
            JobInfo jobInfo = new JobInfo(job, getRecentBuilds(job, recentBuildCount));
            if (jobInfo.hasOnlyFailures(recentBuildCount)) {
                repeatedFailures.add(jobInfo);
            } else {
                otherJobs.add(jobInfo);
            }
        }

        List<JobGroup> groups = new ArrayList<>();
        groups.add(new JobGroup(
                JobFailuresI18n.text("group.failed.title", recentBuildCount),
                JobFailuresI18n.text("group.failed.description", recentBuildCount),
                repeatedFailures));
        groups.add(new JobGroup(
                JobFailuresI18n.text("group.other.title"),
                JobFailuresI18n.text("group.other.description", recentBuildCount),
                otherJobs));
        return groups;
    }

    public int getRecentBuildCount() {
        return JobFailuresConfiguration.get().getRecentBuildCount();
    }

    public String getPageDescription() {
        return JobFailuresI18n.text("page.description", getRecentBuildCount());
    }

    private List<BuildInfo> getRecentBuilds(Job<?, ?> job, int count) {
        List<BuildInfo> recentBuilds = new ArrayList<>();
        for (Object item : job.getBuilds().limit(count)) {
            recentBuilds.add(new BuildInfo((Run<?, ?>) item));
        }
        return recentBuilds;
    }

    public static final class JobGroup {
        private final String title;
        private final String description;
        private final List<JobInfo> jobs;

        JobGroup(String title, String description, List<JobInfo> jobs) {
            this.title = title;
            this.description = description;
            this.jobs = jobs;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getJobCount() {
            return jobs.size();
        }

        public List<JobInfo> getJobs() {
            return jobs;
        }
    }

    public static final class JobInfo {
        private final Job<?, ?> job;
        private final List<BuildInfo> builds;

        JobInfo(Job<?, ?> job, List<BuildInfo> builds) {
            this.job = job;
            this.builds = builds;
        }

        public String getFullDisplayName() {
            return job.getFullDisplayName();
        }

        public String getUrl() {
            return job.getUrl();
        }

        public List<BuildInfo> getBuilds() {
            return builds;
        }

        boolean hasOnlyFailures(int expectedCount) {
            return builds.size() == expectedCount && builds.stream().allMatch(BuildInfo::isFailure);
        }
    }

    public static final class BuildInfo {
        private final Run<?, ?> run;

        BuildInfo(Run<?, ?> run) {
            this.run = run;
        }

        public int getNumber() {
            return run.getNumber();
        }

        public String getUrl() {
            return run.getUrl();
        }

        public String getIconName() {
            return run.getIconColor().getImage();
        }

        public String getStatus() {
            if (run.isBuilding()) {
                return JobFailuresI18n.text("status.running");
            }
            Result result = run.getResult();
            if (result == null) {
                return JobFailuresI18n.text("status.unknown");
            }
            if (result == Result.SUCCESS) {
                return JobFailuresI18n.text("status.success");
            }
            if (result == Result.FAILURE) {
                return JobFailuresI18n.text("status.failure");
            }
            if (result == Result.UNSTABLE) {
                return JobFailuresI18n.text("status.unstable");
            }
            if (result == Result.ABORTED) {
                return JobFailuresI18n.text("status.aborted");
            }
            return result.toString();
        }

        public String getStatusClass() {
            if (run.isBuilding()) {
                return "failure-radar__build--running";
            }
            Result result = run.getResult();
            if (result == Result.SUCCESS) {
                return "failure-radar__build--success";
            }
            if (result == Result.FAILURE) {
                return "failure-radar__build--failure";
            }
            if (result == Result.UNSTABLE) {
                return "failure-radar__build--unstable";
            }
            if (result == Result.ABORTED) {
                return "failure-radar__build--aborted";
            }
            return "failure-radar__build--other";
        }

        public String getAccessibleLabel() {
            return JobFailuresI18n.text("build.accessible", getNumber(), getStatus());
        }

        boolean isFailure() {
            return !run.isBuilding() && run.getResult() == Result.FAILURE;
        }
    }
}
