package configurationslicing.promotedbuilds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import configurationslicing.UnorderedStringSlicer;
import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.plugins.promoted_builds.JobPropertyImpl;
import hudson.plugins.promoted_builds.PromotionProcess;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class PromotedBuildsNameSlicer extends UnorderedStringSlicer<AbstractProject<?,?>> {


    public PromotedBuildsNameSlicer() {super(new PromotedBuildsSlicerSpec()); }

    public static class PromotedBuildsSlicerSpec extends UnorderedStringSlicerSpec<AbstractProject<?,?>> {

        private static final Logger log = LoggerFactory.getLogger(PromotedBuildsSlicerSpec.class);
        public static final String NOTHING = "(NOTHING)";

        @Override
        public String getName() { return "Promoted Builds Names"; }

        @Override
        public String getUrl() { return "promotedbuilds"; }

        @Override
        public String getDefaultValueString() { return ""; }

        @Override
        public String getName(AbstractProject<?,?> item) { return item.getFullName(); }

        @Override
        public List<AbstractProject<?,?>> getWorkDomain() {
            List<AbstractProject<?,?>> filteredProjects = new ArrayList<AbstractProject<?,?>>();
            List<AbstractProject> allProjects = Jenkins.getInstance().getAllItems(AbstractProject.class);

            for (AbstractProject project: allProjects) {
                if (project instanceof Project || project instanceof MatrixProject) {
                    filteredProjects.add(project);
                }
            }
            return filteredProjects;
        }

        @Override
        public List<String> getValues(AbstractProject<?,?> item) {

            JobPropertyImpl property = item.getProperty(JobPropertyImpl.class);

            List<String> valuesList = new ArrayList<String>();

            if (property != null) {
                String del = "";
                for(PromotionProcess process : property.getActiveItems()) {
                    del = (String.format("%s[%s] ", del, process.getName()));
                }
                valuesList.add(del.trim());
            }
            if (valuesList.isEmpty() || valuesList.get(0).equals("")) {
                valuesList.add(NOTHING);
            }

            return valuesList;
        }

        @Override
        public boolean setValues(AbstractProject<?,?> item, List<String> rawValues) {

            JobPropertyImpl property = item.getProperty(JobPropertyImpl.class);

            List<String> values = parseValues(rawValues);

            if (property == null && !values.isEmpty()) {
                property = tryCreateProperty(item);
                if (property == null) {
                    return false;
                }
            }

            if(rawValues.get(0).equals(NOTHING)) {
                property.getActiveItems().removeAll(property.getActiveItems());
            }

            Map<String, PromotionProcess> oldPromotions = new HashMap<String, PromotionProcess>();
            for(PromotionProcess promotionProcess : property.getActiveItems()) {
                oldPromotions.put(promotionProcess.getName(), promotionProcess);
            }

            // Remove old promotions
            for(String oldName : oldPromotions.keySet()) {
                if(!values.contains(oldName)) {
                    property.getActiveItems().remove(oldPromotions.get(oldName));
                }
            }

            // Add new promotions
            for(String newName : values) {
                if(!oldPromotions.keySet().contains(newName)) {
                    try {
                        property.addProcess(newName);
                    } catch (IOException e) {
                        log.error("Failed to add promotion process {} to project {}", newName, item.getName());
                        Throwables.propagate(e);
                        return false;
                    }
                }
            }

            return true;
        }

        private List<String> parseValues(List<String> rawValues) {

            List<String> values = new ArrayList<String>();

            Pattern regex = Pattern.compile("\\[(.*?)\\]");
            Matcher regexMatcher = regex.matcher(rawValues.get(0).replace(" ", ""));
            while (regexMatcher.find()) {
                values.add(regexMatcher.group(1));
            }

            return values;
        }

        private JobPropertyImpl tryCreateProperty(AbstractProject<?,?> item) {
            log.info("Trying to set values on project {} where there is no property", item.getName());
            try {
                item.addProperty(new JobPropertyImpl(item));
                JobPropertyImpl property = item.getProperty(JobPropertyImpl.class);
                log.info("Successfully created property");
                return property;
            } catch (Exception e) {
                log.error("Failed to create new property for project {}", item.getName());
                Throwables.propagate(e);
                return null;
            }
        }

    }

}
