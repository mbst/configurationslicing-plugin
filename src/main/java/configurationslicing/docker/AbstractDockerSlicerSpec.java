package configurationslicing.docker;

import java.io.IOException;
import java.util.List;

import com.cloudbees.dockerpublish.DockerBuilder;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import configurationslicing.UnorderedStringSlicer.UnorderedStringSlicerSpec;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractDockerSlicerSpec
        extends UnorderedStringSlicerSpec<AbstractProject<?, ?>> {

    private static final Logger log = LoggerFactory.getLogger(AbstractDockerSlicerSpec.class);

    public static final String NOTHING = "(Nothing)";
    public static final String DEFAULT = "(Default)";

    public abstract String getSliceParam(DockerBuilder builder);
    public abstract DockerBuilder setSliceParam(DockerBuilder builder, String value);

    @Override
    public String getDefaultValueString() {
        return DEFAULT;
    }

    @Override
    public String getName(AbstractProject<?, ?> item) {
        return item.getDisplayName();
    }

    @Override
    public List<AbstractProject<?, ?>> getWorkDomain() {
        ImmutableList.Builder<AbstractProject<?,?>> filteredProjects = ImmutableList.builder();
        List<AbstractProject> allProjects = Jenkins.getInstance().getAllItems(AbstractProject.class);

        for (AbstractProject project: allProjects) {
            if (project instanceof Project || project instanceof MatrixProject) {
                filteredProjects.add(project);
            }
        }
        return filteredProjects.build();
    }


    @Override
    public List<String> getValues(AbstractProject<?,?> item) {

        List<String> valuesList = Lists.newArrayList();

        DescribableList<Builder, Descriptor<Builder>> buildersList = getBuildersList(item);
        List<DockerBuilder> builders = getDockerBuildersList(buildersList);

        for (DockerBuilder builder: builders) {
            valuesList.add(getSliceParam(builder));
        }
        if (valuesList.isEmpty()) {
            valuesList.add(NOTHING);
        }

        return valuesList;
    }

    @Override
    public boolean setValues(AbstractProject<?,?> item, List<String> values) {

        DescribableList<Builder, Descriptor<Builder>> buildersList = getBuildersList(item);
        List<DockerBuilder> dockerBuildersList = getDockerBuildersList(buildersList);

        int listSize = dockerBuildersList.size();

        DockerBuilder[] oldBuilders = new DockerBuilder[listSize];
        DockerBuilder[] newBuilders = new DockerBuilder[listSize];

        for (int i = 0; i < dockerBuildersList.size(); i++) {
            oldBuilders[i] = dockerBuildersList.get(i);
        }

        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if(value.equals(NOTHING)) {
                continue;
            }
            newBuilders[i] = copyOf(oldBuilders[i]);

            if(oldBuilders[i] != null && !getSliceParam(oldBuilders[i]).equals(value)) {
                if(valueIsNotWritable(value)) {
                    value = "";
                }
                newBuilders[i] = setSliceParam(newBuilders[i], value);
            }
        }

        // perform any replacements
        for (int i = 0; i < oldBuilders.length; i++) {
            if (oldBuilders[i] != null && newBuilders[i] != null && sliceParamIsDifferent(oldBuilders[i], newBuilders[i])) {
                log.info(
                        "Updating DockerBuilder config {} for project {}",
                        getUrl(),
                        item.getFullName()
                );
                replaceBuilder(buildersList, oldBuilders[i], newBuilders[i]);
            }
        }

        // in case a new builder was generated somehow
        checkNoNewBuilders(oldBuilders, newBuilders, listSize);

        return true;
    }

    @SuppressWarnings("unchecked")
    private static DescribableList<Builder,Descriptor<Builder>> getBuildersList(
            AbstractProject<?, ?> item
    ) {
        if (item instanceof Project) {
            return ((Project) item).getBuildersList();
        } else if (item instanceof MatrixProject) {
            return ((MatrixProject) item).getBuildersList();
        } else {
            log.info("No builders found for project: {}", item.getDisplayName());
            return null;
        }

    }

    private List<DockerBuilder> getDockerBuildersList(
            DescribableList<Builder, Descriptor<Builder>> builders
    ) {
        return builders.getAll(DockerBuilder.class);
    }

    private boolean valueIsNotWritable(String value) {
        return value.isEmpty() || value.equals(NOTHING) || value.equals(DEFAULT);
    }

    private void replaceBuilder(
            DescribableList<Builder,Descriptor<Builder>> builders,
            Builder oldBuilder,
            Builder newBuilder
    ) {
        List<Builder> newList = Lists.newArrayList(builders.toList());
        for (int i = 0; i < newList.size(); i++) {
            Builder builder = newList.get(i);
            if (builder == oldBuilder) {
                newList.set(i, newBuilder);
            }
        }

        try {
            builders.replaceBy(newList);
        } catch (IOException e) {
            log.error(
                    "Failed to update DockerBuilder config: {}. Check permissions on config.xml",
                    getUrl(),
                    e
            );
        }
    }

    private void checkNoNewBuilders(DockerBuilder[] oldBuilders, DockerBuilder[] newBuilders, int listSize) {
        for (int i = 0; i < listSize; i++) {
            if (oldBuilders[i] == null && newBuilders[i] != null) {
                Throwables.propagate(
                        new IllegalStateException(
                                "New builder exists when it shouldn't: " + newBuilders[i].getRepoName()
                        )
                );
            }
        }
    }

    private boolean sliceParamIsDifferent(DockerBuilder oldBuilder, DockerBuilder newBuilder) {
        return !getSliceParam(oldBuilder).equals(getSliceParam(newBuilder));
    }

    private DockerBuilder copyOf(DockerBuilder dockerBuilder) {
        return copyOfWithDifferentName(dockerBuilder, dockerBuilder.getRepoName());
    }

    DockerBuilder copyOfWithDifferentName(DockerBuilder oldBuilder, String repoName) {

        DockerBuilder newBuilder = new DockerBuilder(checkNotNull(repoName));

        newBuilder.setBuildAdditionalArgs(oldBuilder.getBuildAdditionalArgs());
        newBuilder.setBuildContext(oldBuilder.getBuildContext());
        newBuilder.setCreateFingerprint(oldBuilder.isCreateFingerprint());
        newBuilder.setDockerfilePath(oldBuilder.getDockerfilePath());
        newBuilder.setDockerToolName(oldBuilder.getDockerToolName());
        newBuilder.setForcePull(oldBuilder.isForcePull());
        newBuilder.setForceTag(oldBuilder.isForceTag());
        newBuilder.setNoCache(oldBuilder.isNoCache());
        newBuilder.setRepoTag(oldBuilder.getRepoTag());
        newBuilder.setRegistry(oldBuilder.getRegistry());
        newBuilder.setServer(oldBuilder.getServer());
        newBuilder.setSkipBuild(oldBuilder.isSkipBuild());
        newBuilder.setSkipDecorate(oldBuilder.isSkipDecorate());
        newBuilder.setSkipPush(oldBuilder.isSkipPush());
        newBuilder.setSkipTagLatest(oldBuilder.isSkipTagLatest());

        return newBuilder;
    }
}
