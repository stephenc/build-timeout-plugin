/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.build_timeout.operations;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.build_timeout.BuildTimeOutJenkinsRule;
import hudson.plugins.build_timeout.BuildTimeOutOperation;
import hudson.plugins.build_timeout.BuildTimeOutOperationDescriptor;
import hudson.plugins.build_timeout.QuickBuildTimeOutStrategy;
import hudson.plugins.build_timeout.BuildTimeoutWrapper;
import hudson.plugins.build_timeout.impl.AbsoluteTimeOutStrategy;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Recorder;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Mailer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.SleepBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 *
 */
public class BuildStepOperationTest {
    @Rule
    public BuildTimeOutJenkinsRule j = new BuildTimeOutJenkinsRule();
    
    @Before
    public void setUp() {
        BuildTimeoutWrapper.MINIMUM_TIMEOUT_MILLISECONDS = 0;
    }
    
    public static class TestBuilder extends Builder {
        public boolean result = true;
        public int executed = 0;
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            listener.getLogger().println(String.format(
                    "%s is exectuted: times=%d",
                    getClass().getName(), ++executed
            ));
            return result;
        }
    }
    
    public static class TestPublisher extends Recorder {
        public boolean result = true;
        public int executed = 0;
        
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            listener.getLogger().println(String.format(
                    "%s is exectuted: times=%d",
                    getClass().getName(), ++executed
            ));
            return result;
        }
    }
    
    @Test
    public void testDisalbed() throws Exception {
        BuildStepOperation.DescriptorImpl d
            = (BuildStepOperation.DescriptorImpl)j.jenkins.getDescriptorOrDie(BuildStepOperation.class);
        // should be disabled by default.
        assertFalse(d.isEnabled());
        
        assertFalse(BuildTimeOutOperationDescriptor.all(FreeStyleProject.class).contains(d));
    }
    
    @Test
    public void testEnabled() throws Exception {
        BuildStepOperation.DescriptorImpl d
            = (BuildStepOperation.DescriptorImpl)j.jenkins.getDescriptorOrDie(BuildStepOperation.class);
        d.setEnabled(true);
        assertTrue(d.isEnabled());
        
        assertTrue(BuildTimeOutOperationDescriptor.all(FreeStyleProject.class).contains(d));
    }
    
    @Test
    public void testBuilder() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        TestBuilder builder1 = new TestBuilder();
        TestBuilder builder2 = new TestBuilder();
        p.getBuildWrappersList().add(new BuildTimeoutWrapper(
                new QuickBuildTimeOutStrategy(5000),
                Arrays.<BuildTimeOutOperation>asList(
                        new BuildStepOperation(builder1, true),
                        new BuildStepOperation(builder2, true)
                )
        ));
        p.getBuildersList().add(new SleepBuilder(9999));
        
        assertEquals(0, builder1.executed);
        assertEquals(0, builder2.executed);
        
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        assertEquals(1, builder1.executed);
        assertEquals(1, builder2.executed);
    }
    
    @Test
    public void testBuilderFailContinue() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        TestBuilder builder1 = new TestBuilder();
        TestBuilder builder2 = new TestBuilder();
        p.getBuildWrappersList().add(new BuildTimeoutWrapper(
                new QuickBuildTimeOutStrategy(5000),
                Arrays.<BuildTimeOutOperation>asList(
                        new BuildStepOperation(builder1, true),
                        new BuildStepOperation(builder2, true)
                )
        ));
        p.getBuildersList().add(new SleepBuilder(9999));
        
        builder1.result = false;
        
        assertEquals(0, builder1.executed);
        assertEquals(0, builder2.executed);
        
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        assertEquals(1, builder1.executed);
        assertEquals(1, builder2.executed);
    }
    
    @Test
    public void testBuilderFailStop() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        TestBuilder builder1 = new TestBuilder();
        TestBuilder builder2 = new TestBuilder();
        p.getBuildWrappersList().add(new BuildTimeoutWrapper(
                new QuickBuildTimeOutStrategy(5000),
                Arrays.<BuildTimeOutOperation>asList(
                        new BuildStepOperation(builder1, false),
                        new BuildStepOperation(builder2, true)
                )
        ));
        p.getBuildersList().add(new SleepBuilder(9999));
        
        builder1.result = false;
        
        assertEquals(0, builder1.executed);
        assertEquals(0, builder2.executed);
        
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        
        assertEquals(1, builder1.executed);
        assertEquals(0, builder2.executed);
    }
    
    @Test
    public void testPublisher() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        TestPublisher publisher1 = new TestPublisher();
        TestPublisher publisher2 = new TestPublisher();
        p.getBuildWrappersList().add(new BuildTimeoutWrapper(
                new QuickBuildTimeOutStrategy(5000),
                Arrays.<BuildTimeOutOperation>asList(
                        new BuildStepOperation(publisher1, true),
                        new BuildStepOperation(publisher2, true)
                )
        ));
        p.getBuildersList().add(new SleepBuilder(9999));
        
        assertEquals(0, publisher1.executed);
        assertEquals(0, publisher2.executed);
        
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        assertEquals(1, publisher1.executed);
        assertEquals(1, publisher2.executed);
    }
    
    @Test
    public void testConfiguration() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        ArtifactArchiver archiver = new ArtifactArchiver("**/*.xml", "exclude.xml", false);
        
        BuildStepOperation op = new BuildStepOperation(archiver, true);
        BuildTimeoutWrapper timeout = new BuildTimeoutWrapper(
                new AbsoluteTimeOutStrategy(3),
                Arrays.<BuildTimeOutOperation>asList(op)
        );
        p.getBuildWrappersList().add(timeout);
        p.save();
        
        String fullname = p.getFullName();
        
        // reconfigure.
        // This should preserve configuration.
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);
        
        p = j.jenkins.getItemByFullName(fullname, FreeStyleProject.class);
        timeout = p.getBuildWrappersList().get(BuildTimeoutWrapper.class);
        op = Util.filter(timeout.getOperationList(), BuildStepOperation.class).get(0);
        archiver = (ArtifactArchiver)op.getBuildstep();
        
        assertEquals("**/*.xml", archiver.getArtifacts());
        assertEquals("exclude.xml", archiver.getExcludes());
        assertFalse(archiver.isLatestOnly());
    }
    
    @Test
    public void testConfigurationWithoutDbc() throws Exception {
        // assert that Mailer does not have a constructor with DataBoundConstructor.
        {
            for(Constructor<?> c: Mailer.class.getConstructors()) {
                assertFalse(c.isAnnotationPresent(DataBoundConstructor.class));
            }
        }
        
        FreeStyleProject p = j.createFreeStyleProject();
        Mailer mailer = new Mailer();
        mailer.recipients = "test@example.com";
        mailer.dontNotifyEveryUnstableBuild = true;
        mailer.sendToIndividuals = true;
        
        BuildStepOperation op = new BuildStepOperation(mailer, true);
        BuildTimeoutWrapper timeout = new BuildTimeoutWrapper(
                new AbsoluteTimeOutStrategy(3),
                Arrays.<BuildTimeOutOperation>asList(op)
        );
        p.getBuildWrappersList().add(timeout);
        p.save();
        
        String fullname = p.getFullName();
        
        // reconfigure.
        // This should preserve configuration.
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);
        
        p = j.jenkins.getItemByFullName(fullname, FreeStyleProject.class);
        timeout = p.getBuildWrappersList().get(BuildTimeoutWrapper.class);
        op = Util.filter(timeout.getOperationList(), BuildStepOperation.class).get(0);
        mailer = (Mailer)op.getBuildstep();
        
        assertEquals("test@example.com", mailer.recipients);
        // mailer.dontNotifyEveryUnstableBuild does not restored, for it is treated in a special way.
        // Detail: there can be multiple "mailer_notifyEveryUnstableBuild"s in a form.
        //assertTrue(mailer.dontNotifyEveryUnstableBuild);
        assertTrue(mailer.sendToIndividuals);
    }
}
