package com.example.graphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Configuration
@PropertySource("classpath:application.properties")
@Slf4j
public class GraphQLConfigurator {

    @Autowired
    private BigQueryRunner bigQueryRunner;

    @Value("${wirings.file:wirings.json}")
    private String wiringsFile;

    @Value("${schema.graphqls:schema.graphqls}")
    private String schemaFile;

    //    @EventListener
//    public void init(WebServerInitializedEvent event) {
//        RouterFunction<ServerResponse> route = RouterFunctions.route(RequestPredicates.path("/graphql"), handle());
//
//    }
//
//    private HandlerFunction<ServerResponse> handle() {
//        return null;
//    }
//    @EventListener
//    public void init(WebServerInitializedEvent event) {
//        GraphQLHandler graphQLHandler = GraphQLHandler.create(setupGraphQL());
//        RouterFunction<ServerResponse> route = RouterFunctions.route(RequestPredicates.POST("/graphql"), graphQLHandler);
//        HttpHandler httpHandler = RouterFunctions.toHttpHandler(route);
//        WebServer server = event.getWebServer();
//        server.startAndAwait(httpHandler);
//    }
//
//    @Bean
//    public RouterFunction<ServerResponse> routes() {
//        GraphQLh
//        return RouterFunctions.route(RequestPredicates.POST("/graphql"), graphQLHandler);
//    }


    @Bean
    public GraphQL setupGraphQL() {
        RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();

        // load the metadata and generate the generic DataFetchers.
        String wirings = null;
        try {
            wirings = new String(getClass().getClassLoader().getResourceAsStream(wiringsFile).readAllBytes());
        } catch (IOException e) {
            log.error("Error reading wirings file", e);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<FieldMetaData> mappings = null;
        try {
            mappings = objectMapper.readValue(wirings, new TypeReference<>() {
            });
        } catch (IOException e) {
            log.error("Error parsing wirings file", e);
        }

        // wire in metadata driven data fetchers
        wiring = wire(wiring, mappings);

        // if you wanted to customise things you would add more wiring here

        // load the schema
        String schema = null;
        try {
            schema = new String(getClass().getClassLoader().getResourceAsStream(schemaFile).readAllBytes());
        } catch (IOException e) {
            log.error("Error reading schema file", e);
        }
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        // return the GraphQL server.
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, wiring.build());
        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    @SneakyThrows
    protected RuntimeWiring.Builder wire(RuntimeWiring.Builder wiring, List<FieldMetaData> mappings) {
        for (FieldMetaData fieldMetaData : mappings) {
            log.info("wiring: {}", fieldMetaData.toString());
            wiring = wiring.type(fieldMetaData.getTypeName(), builder -> {
                // a blocking data fetcher
                DataFetcher blockingDataFetcher = bigQueryRunner.queryForOne(
                        fieldMetaData.getSql(),
                        fieldMetaData.getMapperCsv(),
                        fieldMetaData.getGqlAttr(),
                        fieldMetaData.getSqlParam());
                // wrap in an async data fetcher
                return builder.dataFetcher(fieldMetaData.getFieldName(),
                        (de) -> CompletableFuture.supplyAsync(() -> {
                            try {
                                return blockingDataFetcher.get(de);
                            } catch (Exception e) {
                                log.error("Exception {} with query: {}", e.getMessage(), mappings.toString());
                                return null;
                            }
                        }));
            });
        }
        return wiring;
    }
}