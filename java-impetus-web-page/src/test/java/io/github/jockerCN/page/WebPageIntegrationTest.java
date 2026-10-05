package io.github.jockerCN.page;

import io.github.jockerCN.JavaImpetusSpringAutoConfiguration;
import io.github.jockerCN.JavaImpetusWebAutoConfiguration;
import io.github.jockerCN.exception.EnableGlobalException;
import io.github.jockerCN.jackson.JavaImpetusJacksonAutoConfiguration;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.query.model.QueryPair;
import io.github.jockerCN.web.EnableJacksonConverters;
import io.github.jockerCN.web.binding.EnableWebBinding;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.format.FormatterRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import testfixture.webpage.modules.TestPageParam;
import testfixture.webpage.external.ExternalPageParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebPageIntegrationTest {

    // PageUtils intentionally caches the manager for a single application. Context tests use that same bean.
    private static final JpaQueryManager QUERY_MANAGER = mock(JpaQueryManager.class);

    @BeforeEach
    void resetManager() {
        reset(QUERY_MANAGER);
        when(QUERY_MANAGER.queryList(any())).thenReturn(List.of(Map.of("name", "plain")));
        when(QUERY_MANAGER.count(any())).thenReturn(7L);
    }

    private WebApplicationContextRunner baseRunner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                JavaImpetusWebAutoConfiguration.class, JavaImpetusSpringAutoConfiguration.class,
                JavaImpetusJacksonAutoConfiguration.class, JacksonAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class,
                ValidationAutoConfiguration.class));
    }

    private WebApplicationContextRunner runner() {
        return baseRunner().withUserConfiguration(WebFeatures.class)
                .withBean(JpaQueryManager.class, () -> QUERY_MANAGER);
    }

    @Test
    void applicationScansControllerAndAnnotationMapsOnlyParameterClasses() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PageController.class);
            assertThat(context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers())
                    .anyMatch(ModuleParamArgumentResolver.class::isInstance);
            assertThat(context).doesNotHaveBean(TestPageParam.class).doesNotHaveBean(PageModuleAnnotationFilter.class);
            assertThat(context.getBeanFactory().containsBeanDefinition("testPageParam")).isFalse();
            assertThat(context.containsBean("scanned")).isFalse();
            assertThat(context.getBeanFactory().containsBeanDefinition("unannotatedParam")).isFalse();
            mvc(context).perform(get("/module/page").param("module", "scanned").param("name", "Ada")
                            .param("page", "3").param("pageSize", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("plain"))
                    .andExpect(jsonPath("$.data.size").value(2));
            TestPageParam param = capturedParam();
            assertThat(param.getName()).isEqualTo("Ada");
            assertThat(param.getPage()).isEqualTo(3);
            assertThat(param.getPageSize()).isEqualTo(2);
            verify(QUERY_MANAGER).count(same(param));
            verify(QUERY_MANAGER, never()).queryListEnhanced(any());
        });
    }

    @Test
    void controllerIsNotRegisteredByAutoConfiguration() {
        baseRunner().withBean(JpaQueryManager.class, () -> QUERY_MANAGER)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PageController.class));
    }

    @Test
    void requestParametersAreFreshAndInputDefaultsAreNotShared() {
        runner().run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/module/page").param("module", "scanned").param("name", "first").param("page", "4"))
                    .andExpect(status().isOk());
            mvc.perform(get("/module/page").param("module", "scanned")).andExpect(status().isOk());
            var captured = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(QUERY_MANAGER, times(2)).queryList(captured.capture());
            TestPageParam first = (TestPageParam) captured.getAllValues().getFirst();
            TestPageParam second = (TestPageParam) captured.getAllValues().getLast();
            assertThat(second).isNotSameAs(first);
            assertThat(first.getName()).isEqualTo("first");
            assertThat(second.getName()).isNull();
            assertThat(second.getPage()).isZero();
        });
    }

    @Test
    void missingBlankAndUnknownModulesNeverReachJpa() {
        runner().run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/module/page")).andExpect(status().isBadRequest());
            mvc.perform(get("/module/page").param("module", " ")).andExpect(status().isBadRequest());
            mvc.perform(get("/module/page").param("module", "unknown")).andExpect(status().isBadRequest());
            verifyNoInteractions(QUERY_MANAGER);
        });
    }

    @Test
    void bindingAndBeanValidationErrorsNeverReachJpa() {
        runner().run(context -> {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/module/page").param("module", "scanned").param("page", "text"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/module/page").param("module", "scanned").param("page", "-1"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/module/page").param("module", "scanned").param("pageSize", "0"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/module/page").param("module", "scanned").param("date", "bad-date"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(QUERY_MANAGER);
        });
    }

    @Test
    void dateFormatsAndPairsUseTheSharedMvcConversionService() {
        runner().run(context -> {
            mvc(context).perform(get("/module/page").param("module", "scanned")
                            .param("range", "4", "9").param("dates", "2026-10-03,2026-10-04")
                            .param("date", "2026/10/04").param("formatted", "04_10_2026")
                            .param("formattedDates", "03_10_2026", "04_10_2026")
                            .param("dateTime", "2026-10-04 12:30:00").param("time", "12:30:00")
                            .param("offset", "2026-10-04T12:30:00+08:00"))
                    .andExpect(status().isOk());
            TestPageParam param = capturedParam();
            assertThat(param.getRange().first()).isEqualTo(4);
            assertThat(param.getRange().second()).isEqualTo(9);
            assertThat(param.getDates().first()).isEqualTo(LocalDate.of(2026, 10, 3));
            assertThat(param.getDates().second()).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(param.getFormattedDates().first()).isEqualTo(LocalDate.of(2026, 10, 3));
            assertThat(param.getFormatted()).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(param.getDate()).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(param.getDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 4, 12, 30));
            assertThat(param.getTime()).isEqualTo(LocalTime.of(12, 30));
            assertThat(param.getOffset()).isEqualTo(OffsetDateTime.parse("2026-10-04T12:30:00+08:00"));
        });
    }

    @Test
    void pairCardinalityAndEndpointErrorsAreBindingErrors() {
        runner().run(context -> {
            MockMvc mvc = mvc(context);
            for (String value : List.of("1", "1,2,3", "x,2")) {
                mvc.perform(get("/module/page").param("module", "scanned").param("range", value))
                        .andExpect(status().isBadRequest());
            }
            verifyNoInteractions(QUERY_MANAGER);
        });
    }

    @Test
    void applicationInitBinderCanRestrictFieldsWithoutChangingStringNullSemantics() {
        runner().withBean(OwnerBindingAdvice.class).run(context -> {
            mvc(context).perform(get("/module/page").param("module", "scanned")
                            .param("name", "").param("ownerId", "123"))
                    .andExpect(status().isOk());
            TestPageParam param = capturedParam();
            assertThat(param.getOwnerId()).isNull();
            assertThat(param.getName()).isEmpty();
        });
    }

    @Test
    void emptyPageKeepsPageUtilsBehaviorWithoutAnExtraCount() {
        when(QUERY_MANAGER.queryList(any())).thenReturn(List.of());
        runner().run(context -> {
            mvc(context).perform(get("/module/page").param("module", "scanned").param("page", "99"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
            verify(QUERY_MANAGER, never()).count(any());
        });
    }

    @Test
    void duplicateAnnotatedKeysFailStartupWithBothTypes() {
        baseRunner().withUserConfiguration(DuplicateModules.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("Duplicate PageModule key 'collision'")
                            .hasStackTraceContaining("FirstPageParam").hasStackTraceContaining("SecondPageParam");
                });
    }

    @Test
    void componentScanMapsParametersWithoutAnAutoConfigurationPackage() {
        baseRunner().withUserConfiguration(ScannedModules.class)
                .run(context -> {
                    assertThat(context.getBean(ModuleParamArgumentResolver.class).getQueryParamType("scanned"))
                            .isEqualTo(TestPageParam.class);
                    assertThat(context.containsBean("ordinaryComponent")).isTrue();
                    assertThat(context).doesNotHaveBean(TestPageParam.class).doesNotHaveBean(PageModuleAnnotationFilter.class);
                });
    }

    @Test
    void autoConfigurationPackagesDoNotDiscoverUnscannedParameters() {
        baseRunner().withUserConfiguration(PackageOnly.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> context.getBean(ModuleParamArgumentResolver.class)
                            .getQueryParamType("scanned")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                });
    }

    @Test
    void componentScanCanIncludeParametersOutsideTheApplicationPackageWithoutInstantiatingThem() {
        ExternalPageParam.CONSTRUCTIONS.set(0);
        baseRunner().withUserConfiguration(PackageOnly.class, ExternalModules.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ModuleParamArgumentResolver.class).getQueryParamType("external"))
                    .isEqualTo(ExternalPageParam.class);
            assertThat(ExternalPageParam.CONSTRUCTIONS).hasValue(0);
            assertThat(context).doesNotHaveBean(ExternalPageParam.class).doesNotHaveBean(PageModuleAnnotationFilter.class);
            assertThat(context.getBeanFactory().containsBeanDefinition("externalPageParam")).isFalse();
            var mappings = PageModuleRegistry.get(context.getBeanFactory()).modules();
            assertThat(mappings).containsOnlyKeys("external");
        });
    }

    @Test
    void concurrentRequestsNeverShareParameterInstances() {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        when(QUERY_MANAGER.queryList(any())).thenAnswer(call -> {
            TestPageParam param = call.getArgument(0);
            seen.add(param);
            return List.of(Map.of("name", param.getName()));
        });
        runner().run(context -> {
            MockMvc mvc = mvc(context);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = java.util.stream.IntStream.range(0, 40).mapToObj(index -> executor.submit(() -> {
                    mvc.perform(get("/module/page").param("module", "scanned").param("name", "request-" + index))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("request-" + index));
                    return null;
                })).toList();
                for (var future : futures) {
                    future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
            assertThat(seen).hasSize(40);
            verify(QUERY_MANAGER, times(40)).count(any());
        });
    }

    @Test
    void customResolverBacksOffAndIsTheOneRegisteredWithMvc() {
        ModuleParamArgumentResolver resolver = new ModuleParamArgumentResolver(Map.of("custom", TestPageParam.class));
        runner().withBean(ModuleParamArgumentResolver.class, () -> resolver).run(context -> {
            assertThat(context).hasSingleBean(ModuleParamArgumentResolver.class);
            assertThat(context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers()).contains(resolver);
            mvc(context).perform(get("/module/page").param("module", "custom")).andExpect(status().isOk());
            mvc(context).perform(get("/module/page").param("module", "scanned")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void overlappingComponentScansDoNotTreatTheSameClassAsADuplicate() {
        runner().withUserConfiguration(ScannedModules.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void moduleMappingsNeverLeakBetweenApplicationContexts() {
        baseRunner().withUserConfiguration(ExternalModules.class).run(context -> {
            PageModuleRegistry registry = PageModuleRegistry.get(context.getBeanFactory());
            var modules = registry.modules();
            assertThat(modules).containsOnlyKeys("external");
            assertThat(registry.modules()).isSameAs(modules);
        });
        baseRunner().withUserConfiguration(ScannedModules.class).run(context -> {
            PageModuleRegistry registry = PageModuleRegistry.get(context.getBeanFactory());
            var modules = registry.modules();
            assertThat(modules).containsOnlyKeys("scanned");
            assertThat(registry.modules()).isSameAs(modules);
        });
    }

    @Test
    void applicationConfigurerCanOverridePairConversionAndKeepItsOwnResolver() {
        HandlerMethodArgumentResolver custom = mock(HandlerMethodArgumentResolver.class);
        runner().withUserConfiguration(UserPairConfiguration.class)
                .withBean("otherConfigurer", WebMvcConfigurer.class, () -> new WebMvcConfigurer() {
                    @Override
                    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                        resolvers.add(custom);
                    }
                }).run(context -> {
                    assertThat(context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers())
                            .contains(custom).anyMatch(ModuleParamArgumentResolver.class::isInstance);
                    mvc(context).perform(get("/module/page").param("module", "scanned").param("range", "1,2"))
                            .andExpect(status().isOk());
                    assertThat(capturedParam().getRange()).isEqualTo(new QueryPair<>(90, 91));
                });
    }

    @Test
    void namedMvcConfigurerOverrideOnlyReplacesTheLibraryIntegration() {
        runner().withBean("modulePageMvcConfigurer", WebMvcConfigurer.class, () -> new WebMvcConfigurer() { })
                .run(context -> assertThat(context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers())
                        .noneMatch(ModuleParamArgumentResolver.class::isInstance));
    }

    private TestPageParam capturedParam() {
        var captured = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(QUERY_MANAGER).queryList(captured.capture());
        return (TestPageParam) captured.getValue();
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = PageController.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PageController.class))
    @ComponentScan(basePackageClasses = TestPageParam.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
    @EnableGlobalException
    @EnableJacksonConverters
    @EnableWebBinding
    static class WebFeatures {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = TestPageParam.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
    static class ScannedModules {
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = "testfixture.webpage.modules")
    static class PackageOnly {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "testfixture.webpage.duplicate", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
    static class DuplicateModules {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ExternalPageParam.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = PageModuleAnnotationFilter.class))
    static class ExternalModules {
    }

    @ControllerAdvice
    static class OwnerBindingAdvice {
        @InitBinder("queryParam")
        void restrict(WebDataBinder binder) {
            binder.setDisallowedFields("ownerId");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserPairConfiguration {
        @Bean
        WebMvcConfigurer userPairConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addFormatters(@NonNull FormatterRegistry registry) {
                    registry.addConverter(new GenericConverter() {
                        @Override
                        public Set<ConvertiblePair> getConvertibleTypes() {
                            return Set.of(new ConvertiblePair(String.class, QueryPair.class));
                        }

                        @Override
                        public Object convert(Object source, @NonNull TypeDescriptor sourceType, @NonNull TypeDescriptor targetType) {
                            return new QueryPair<>(90, 91);
                        }
                    });
                }
            };
        }
    }
}
