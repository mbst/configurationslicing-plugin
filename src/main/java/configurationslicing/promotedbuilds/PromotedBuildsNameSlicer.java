package configurationslicing.promotedbuilds;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

        private static final Pattern STRING_IN_SQUARE_BRACKETS = Pattern.compile("\\[(.*?)\\]");

        public static final String NOTHING = "(NOTHING)";

        @Override
        public String getName() { return "Promoted Builds Names"; }

        @Override
        public String getUrl() { return "promotedbuilds"; }

        // This sliced field has no default value
        @Override
        public String getDefaultValueString() { return NOTHING; }

        @Override
        public String getName(AbstractProject<?,?> item) { return item.getFullName(); }

        @Override
        public List<AbstractProject<?,?>> getWorkDomain() {
            List<AbstractProject<?,?>> filteredProjects = Lists.newArrayList();
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

            List<String> valuesList = Lists.newArrayList();

            if (property != null && !property.getActiveItems().isEmpty()) {
                String nameString = "";
                for(PromotionProcess process : property.getActiveItems()) {
                    nameString = String.format("%s[%s] ", nameString, process.getName());
                }
                valuesList.add(nameString.trim());
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

            if (property == null) {
                if (!values.isEmpty()) {
                    property = tryCreateProperty(item);
                    if (property == null) {
                        return false;
                    }
                } else {
                    return true;
                }
            }

            if(rawValues.get(0).equals(NOTHING)) {
                property.getActiveItems().removeAll(property.getActiveItems());
                return true;
            }

            Map<String, PromotionProcess> oldPromotions = Maps.newHashMap();
            for(PromotionProcess promotionProcess : property.getActiveItems()) {
                oldPromotions.put(promotionProcess.getName(), promotionProcess);
            }

            // Remove old promotions
            for(Map.Entry<String, PromotionProcess> oldEntry : oldPromotions.entrySet()) {
                if(!values.contains(oldEntry.getKey())) {
                    property.getActiveItems().remove(oldEntry.getValue());
                }
            }

            // Add new promotions
            for(String newName : values) {
                if (!oldPromotions.keySet().contains(newName)) {
                    try {
                        property.addProcess(newName);
                    } catch (IOException e) {
                        log.error("Failed to add promotion process {} to project {}", newName, item.getName());
                        Throwables.propagate(e);
                        return false;
                    }
                }
            }
            try {
                item.addProperty(property);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            return true;
        }

        private List<String> parseValues(List<String> rawValues) {

            List<String> values = Lists.newArrayList();

            Matcher regexMatcher = STRING_IN_SQUARE_BRACKETS.matcher(rawValues.get(0).replace(" ", ""));
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
