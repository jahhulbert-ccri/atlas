/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metadata.discovery.graph;

import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanProperty;
import com.thinkaurelius.titan.core.TitanVertex;
import org.apache.hadoop.metadata.discovery.DiscoveryException;
import org.apache.hadoop.metadata.discovery.DiscoveryService;
import org.apache.hadoop.metadata.query.Expressions;
import org.apache.hadoop.metadata.query.GremlinEvaluator;
import org.apache.hadoop.metadata.query.GremlinQuery;
import org.apache.hadoop.metadata.query.GremlinQueryResult;
import org.apache.hadoop.metadata.query.GremlinTranslator;
import org.apache.hadoop.metadata.query.QueryParser;
import org.apache.hadoop.metadata.query.QueryProcessor;
import org.apache.hadoop.metadata.repository.MetadataRepository;
import org.apache.hadoop.metadata.repository.graph.GraphProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Either;
import scala.util.parsing.combinator.Parsers;

import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphBackedDiscoveryService implements DiscoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(GraphBackedDiscoveryService.class);

    private final TitanGraph titanGraph;
    private final DefaultGraphPersistenceStrategy graphPersistenceStrategy;

    @Inject
    GraphBackedDiscoveryService(GraphProvider<TitanGraph> graphProvider,
                                MetadataRepository metadataRepository) throws DiscoveryException {
        this.titanGraph = graphProvider.get();
        this.graphPersistenceStrategy = new DefaultGraphPersistenceStrategy(metadataRepository);
    }

    /**
     * Search using query DSL.
     *
     * @param dslQuery query in DSL format.
     * @return JSON representing the type and results.
     */
    @Override
    public String searchByDSL(String dslQuery) throws DiscoveryException {
        QueryParser queryParser = new QueryParser();
        Either<Parsers.NoSuccess, Expressions.Expression> either = queryParser.apply(dslQuery);
        if (either.isRight()) {
            Expressions.Expression expression = either.right().get();
            GremlinQueryResult queryResult = evaluate(expression);
            return queryResult.toJson();
        }

        throw new DiscoveryException("Invalid expression : " + dslQuery);
    }

    private GremlinQueryResult evaluate(Expressions.Expression expression) {
        Expressions.Expression validatedExpression = QueryProcessor.validate(expression);
        GremlinQuery gremlinQuery =
                new GremlinTranslator(validatedExpression, graphPersistenceStrategy).translate();
        System.out.println("---------------------");
        System.out.println("Query = " + validatedExpression);
        System.out.println("Expression Tree = " + validatedExpression.treeString());
        System.out.println("Gremlin Query = " + gremlinQuery.queryStr());
        System.out.println("---------------------");
        return new GremlinEvaluator(gremlinQuery, graphPersistenceStrategy, titanGraph).evaluate();
    }

    /**
     * Assumes the User is familiar with the persistence structure of the Repository.
     * The given query is run uninterpreted against the underlying Graph Store.
     * The results are returned as a List of Rows. each row is a Map of Key,Value pairs.
     *
     * @param gremlinQuery query in gremlin dsl format
     * @return List of Maps
     * @throws org.apache.hadoop.metadata.discovery.DiscoveryException
     */
    @Override
    public List<Map<String, String>> searchByGremlin(String gremlinQuery)
            throws DiscoveryException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("gremlin-groovy");
        Bindings bindings = engine.createBindings();
        bindings.put("g", titanGraph);

        try {
            Object o = engine.eval(gremlinQuery, bindings);
            if (!(o instanceof List)) {
                throw new DiscoveryException(
                        String.format("Cannot process gremlin result %s", o.toString()));
            }

            List l = (List) o;
            List<Map<String, String>> result = new ArrayList<>();
            for (Object r : l) {

                Map<String, String> oRow = new HashMap<>();
                if (r instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> iRow = (Map) r;
                    for (Map.Entry e : iRow.entrySet()) {
                        Object k = e.getKey();
                        Object v = e.getValue();
                        oRow.put(k.toString(), v.toString());
                    }
                } else if (r instanceof TitanVertex) {
                    Iterable<TitanProperty> ps = ((TitanVertex) r).getProperties();
                    for (TitanProperty tP : ps) {
                        String pName = tP.getPropertyKey().getName();
                        Object pValue = ((TitanVertex) r).getProperty(pName);
                        if (pValue != null) {
                            oRow.put(pName, pValue.toString());
                        }
                    }

                } else if (r instanceof String) {
                    oRow.put("", r.toString());
                } else {
                    throw new DiscoveryException(
                            String.format("Cannot process gremlin result %s", o.toString()));
                }

                result.add(oRow);
            }
            return result;

        } catch (ScriptException se) {
            throw new DiscoveryException(se);
        }
    }
}