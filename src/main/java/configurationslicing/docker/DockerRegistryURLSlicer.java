package configurationslicing.docker;

import com.cloudbees.dockerpublish.DockerBuilder;
import com.google.common.base.Objects;
import configurationslicing.UnorderedStringSlicer;
import hudson.Extension;
import hudson.model.AbstractProject;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;

@Extension
public class DockerRegistryURLSlicer extends UnorderedStringSlicer<AbstractProject<?,?>> {

    public DockerRegistryURLSlicer() {super (new DockerSlicerSpec()); }

    public static class DockerSlicerSpec extends
            AbstractDockerSlicerSpec {

        @Override
        public String getName() {
            return "Docker Slicer - Registry URL";
        }

        @Override
        public String getUrl() {
            return "dockerregistryurl";
        }

        @Override
        public String getSliceParam(DockerBuilder builder) {

            return Objects.firstNonNull(builder.getRegistry().getUrl(), NOTHING);
        }

        @Override
        public DockerBuilder setSliceParam(DockerBuilder builder, String value) {
            DockerRegistryEndpoint dockerRegistry = new DockerRegistryEndpoint(
                    value,
                    builder.getRegistry().getCredentialsId()
            );

            builder.setRegistry(dockerRegistry);
            return builder;
        }

    }
}
