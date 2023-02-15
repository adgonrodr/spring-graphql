//package com.example.graphql;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import graphql.GraphQL;
//import graphql.schema.*;
//import graphql.schema.idl.*;
//import io.vertx.ext.web.handler.graphql.GraphQLHandler;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import lombok.val;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.util.FileCopyUtils;
//import org.springframework.web.servlet.function.RouterFunction;
//import org.springframework.web.servlet.function.RouterFunctions;
//import org.springframework.web.servlet.function.ServerRequest;
//import org.springframework.web.servlet.function.ServerResponse;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.TreeMap;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Configuration
//public class GraphQLConfiguratorOld {
//    @Autowired
//    private BigQueryRunner bigQueryRunner;
//
//    @Value("${wirings.file:wirings.json}")
//    private String wiringsFile;
//
//    @Value("${schema.graphqls:schema.graphqls}")
//    private String schemaFile;
//
//    @Bean
//    public RouterFunction<ServerResponse> graphqlRouter() {
//        return RouterFunctions.route()
//                .POST("/graphql", this::handleGraphQLRequest)
//                .build();
//    }
//
//    @SneakyThrows
//    private ServerResponse handleGraphQLRequest(ServerRequest request) {
//        GraphQLHandler graphQLHandler = GraphQLHandler.create(setupGraphQL());
//        return graphQLHandler.process(request);
//    }
//
//    @SneakyThrows
//    private GraphQL setupGraphQL() {
//        RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();
//
//        // wire in caching
//        wiring = wiring.directiveWiring(new FieldQueryCache());
//
//        // load the metadata and generate the generic DataFetchers.
//        String wirings = new String(FileCopyUtils.copyToByteArray(wiringsResource.getInputStream()),
//                StandardCharsets.UTF_8);
//        ObjectMapper objectMapper = new ObjectMapper();
//        List<FieldMetaData> mappings = objectMapper.readValue(wirings, new TypeReference<>() {});
//
//        // wire in metadata driven data fetchers
//        wiring = wire(wiring, mappings);
//
//        // if you wanted to customise things you would add more wiring here
//
//        // load the schema
//        String schema = new String(FileCopyUtils.copyToByteArray(schemaResource.getInputStream()),
//                StandardCharsets.UTF_8);
//        SchemaParser schemaParser = new SchemaParser();
//        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
//        SchemaGenerator schemaGenerator = new SchemaGenerator();
//
//        // return the GraphQL server.
//        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, wiring.build());
//        return GraphQL.newGraphQL(graphQLSchema).build();
//    }
//
//    class FieldQueryCache implements SchemaDirectiveWiring {
//        @Override
//        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
//            GraphQLFieldDefinition field = environment.getElement();
//
//            val directive = field.getDirective("cache");
//            if (directive != null) {
//                int ms = (int) directive.getArgument("ms").getArgumentValue().getValue();
//                final Cache<String, CompletableFuture> cache = CacheBuilder.newBuilder()
//                        .expireAfterWrite(ms, TimeUnit.MILLISECONDS)
//                        .build();
//
//                GraphQLFieldsContainer parentType = environment.getFieldsContainer();
//                final DataFetcher uncachedDataFetcher = environment.getCodeRegistry().getDataFetcher(parentType, field);
//
//                DataFetcher cachedDataFetcher = (env) -> {
//                    val argMapSortedByKey = new TreeMap<>(env.getArguments());
//                    val cacheKey = argMapSortedByKey.entrySet().stream()
//                            .map(e -> e.getKey() + "=" + e.getValue().toString())
//                            .collect(Collectors.joining(","));
//                    var value = cache.getIfPresent(cacheKey);
//                    if (value == null) {
//                        value = (CompletableFuture) uncachedDataFetcher.get(env);
//                        if (value != null) {
//                            cache.put(cacheKey, value);
//                        }
//                    }
//                    return value;
//                };
//
//                FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, field);
//                environment.getCodeRegistry().dataFetcher(coordinates, cachedDataFetcher);
//            }
//
//            return field;
//        }
//    }
//
//    protected RuntimeWiring.Builder wire(RuntimeWiring.Builder wiring, List<FieldMetaData> mappings) {
//        for (FieldMetaData fieldMetaData : mappings) {
//            log.info("wiring: {}", fieldMetaData.toString());
//            wiring = wiring.type(fieldMetaData.typeName, builder -> {
//                DataFetcher blockingDataFetcher = bigQueryRunner.queryForOne(
//                        fieldMetaData.sql,
//                        fieldMetaData.mapperCsv,
//                        fieldMetaData.gqlAttr,
//                        fieldMetaData.sqlParam);
//                return builder.dataFetcher(fieldMetaData.fieldName,
//                        (de) -> CompletableFuture.supplyAsync(() -> {
//                            try {
//                                return blockingDataFetcher.get(de);
//                            } catch (Exception e) {
//                                log.error("Exception {} with query: {}", e.getMessage(), mappings.toString());
//                                return null;
//                            }
//                        }));
//            });
//        }
//        return wiring;
//    }
//}