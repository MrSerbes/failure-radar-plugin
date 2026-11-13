package io.jenkins.plugins.jobfailure;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/** Marks a job for inclusion in the Failure Radar page. */
public final class FailureTrackingProperty extends JobProperty<Job<?, ?>> {
    private final boolean enabled;

    @DataBoundConstructor
    public FailureTrackingProperty(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public DescriptorImpl() {
            super(FailureTrackingProperty.class);
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return JobFailuresI18n.text("plugin.trackFailures");
        }
    }
}
