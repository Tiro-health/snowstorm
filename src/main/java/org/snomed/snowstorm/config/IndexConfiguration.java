package org.snomed.snowstorm.config;

import io.kaicode.elasticvc.repositories.config.IndexNameProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Separate configuration for IndexNameProvider bean to avoid circular dependency.
 *
 * This class is intentionally separate from ElasticsearchConfig to ensure that
 * the indexNameProvider bean is fully initialized before ElasticsearchConfig's
 * @PostConstruct init() method runs and scans entities that reference it via SpEL.
 */
@Configuration
public class IndexConfiguration {

	@Value("${elasticsearch.index.prefix}")
	private String indexNamePrefix;

	@Bean
	public IndexNameProvider indexNameProvider() {
		return new IndexNameProvider(indexNamePrefix);
	}
}
