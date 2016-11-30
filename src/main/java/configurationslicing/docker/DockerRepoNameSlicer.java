package configurationslicing.docker;

import com.cloudbees.dockerpublish.DockerBuilder;
import configurationslicing.UnorderedStringSlicer;
import hudson.Extension;
import hudson.model.AbstractProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class DockerRepoNameSlicer extends UnorderedStringSlicer<AbstractProject<?,?>> {

    public DockerRepoNameSlicer() {super (new DockerSlicerSpec()); }

    public static class DockerSlicerSpec extends
            AbstractDockerSlicerSpec {

        private static final Logger log = LoggerFactory.getLogger(DockerRepoNameSlicer.class);

        @Override
        public String getName() {
            return "Docker Slicer - Repo Name";
        }

        @Override
        public String getUrl() {
            return "dockerreponame";
        }

        @Override
        public String getSliceParam(DockerBuilder builder) {
            return builder.getRepoName();
        }

        @Override
        public DockerBuilder setSliceParam(DockerBuilder builder, String value) {
            // setting repo name isn't exposed by the api so we're copying the current one and
            // replacing the repo name using the constructor
            if(value == null || value.isEmpty()) {
                log.error("Repo name cannot be null or empty for: {}. Builder was not updated.", builder.getRepoName());
                return builder;
            }
            return setRepoName(builder, value);
        }

        private DockerBuilder setRepoName(DockerBuilder oldBuilder, String repoName) {
            return copyOfWithDifferentName(oldBuilder, repoName);
        }

    }
}
