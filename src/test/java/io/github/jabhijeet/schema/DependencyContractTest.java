package io.github.jabhijeet.schema;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyContractTest {

    @Test
    void parquet_runtime_dependencies_are_transitive() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());

        assertThat(dependencyIsTransitive(pom, "hadoop-common")).isTrue();
        assertThat(dependencyIsTransitive(pom, "hadoop-mapreduce-client-core")).isTrue();
    }

    private static boolean dependencyIsTransitive(Document pom, String artifactId) {
        var dependencies = pom.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = (org.w3c.dom.Element) dependencies.item(i);
            var ids = dependency.getElementsByTagName("artifactId");
            if (ids.getLength() == 0 || !artifactId.equals(ids.item(0).getTextContent())) continue;
            var optional = dependency.getElementsByTagName("optional");
            return optional.getLength() == 0 || !"true".equalsIgnoreCase(optional.item(0).getTextContent());
        }
        return false;
    }
}
