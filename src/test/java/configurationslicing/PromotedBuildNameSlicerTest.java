package configurationslicing;

import com.google.common.collect.Lists;
import configurationslicing.promotedbuilds.PromotedBuildsNameSlicer.PromotedBuildsSlicerSpec;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.plugins.promoted_builds.JobPropertyImpl;
import hudson.plugins.promoted_builds.PromotionProcess;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.List;

public class PromotedBuildNameSlicerTest extends HudsonTestCase {

    private String expectedPromotionOne = "promotionOne";
    private String expectedPromotionTwo = "promotionTwo";

    private String rawValueStringOne = String.format("[%s]", expectedPromotionOne);
    private String rawValueStringTwo = String.format("[%s]", expectedPromotionTwo);
    private String rawValueStringMultiple = String.format("[%s] [%s]", expectedPromotionOne, expectedPromotionTwo);

    private List<String> rawValueListOne = Lists.newArrayList(rawValueStringOne);
    private List<String> rawValueListTwo = Lists.newArrayList(rawValueStringTwo);
    private List<String> rawValueListMultiple = Lists.newArrayList(rawValueStringMultiple);



    public void testAddNewPromotionProcessToProjectWithNoProperty() throws Exception {
        PromotedBuildsSlicerSpec spec = new PromotedBuildsSlicerSpec();
        AbstractProject<?,?> project = createUnpopulatedProject();

        assert(spec.getValues(project).equals(Lists.newArrayList(spec.NOTHING)));

        assert(spec.setValues(project, rawValueListOne));

        List<String> values = spec.getValues(project);
        assert(values.get(0).equals(rawValueStringOne));

        List<PromotionProcess> promotionProcesses = project.getProperty(JobPropertyImpl.class).getActiveItems();
        assert(promotionProcesses.size() == 1);
        assert(promotionProcesses.get(0).getName().equals(expectedPromotionOne));
    }

    public void testAddNewPromotionProcessToPopulatedProjectProperty() throws Exception {
        PromotedBuildsSlicerSpec spec = new PromotedBuildsSlicerSpec();
        AbstractProject<?,?> project = createPopulatedProject();

        project.getProperty(JobPropertyImpl.class).addProcess(expectedPromotionOne);

        assert(spec.getValues(project).equals(rawValueListOne));

        assert(spec.setValues(project, rawValueListMultiple));

        List<String> values = spec.getValues(project);
        assert(values.get(0).equals(rawValueStringMultiple));

        List<PromotionProcess> promotionProcesses = project.getProperty(JobPropertyImpl.class).getActiveItems();
        assert(promotionProcesses.size() == 2);
        assert(promotionProcesses.get(0).getName().equals(expectedPromotionOne));
        assert(promotionProcesses.get(1).getName().equals(expectedPromotionTwo));

    }

    public void testRemovePromotionProcessFromPopulatedProjectProperty() throws Exception {
        PromotedBuildsSlicerSpec spec = new PromotedBuildsSlicerSpec();
        AbstractProject<?,?> project = createPopulatedProject();

        project.getProperty(JobPropertyImpl.class).addProcess(expectedPromotionOne);
        project.getProperty(JobPropertyImpl.class).addProcess(expectedPromotionTwo);

        assert(spec.getValues(project).equals(rawValueListMultiple));

        assert(spec.setValues(project, rawValueListTwo));

        List<String> values = spec.getValues(project);
        assert(values.get(0).equals(rawValueStringTwo));

        List<PromotionProcess> promotionProcesses = project.getProperty(JobPropertyImpl.class).getActiveItems();
        assert(promotionProcesses.size() == 1);
        assert(promotionProcesses.get(0).getName().equals(expectedPromotionTwo));

    }

    private Project createPopulatedProject() throws Exception {
        Project project = createFreeStyleProject();
        project.addProperty(new JobPropertyImpl(project));

        return project;
    }

    private Project createUnpopulatedProject() throws Exception {
        return createFreeStyleProject();
    }
}
