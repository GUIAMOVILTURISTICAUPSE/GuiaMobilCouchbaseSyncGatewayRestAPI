package main.java;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.query.AsyncN1qlQueryResult;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.ParameterizedN1qlQuery;

public class DatabaseForRest {

	 private DatabaseForRest() { }

	    public static Map<String, Object> getById(final Bucket bucket, String todoId) {
	        String queryStr = "SELECT "+bucket.name()+".*, " +"META("+ bucket.name()+").id " +
	                "FROM `" + bucket.name() + "` " +
	                "WHERE documentClass = 'class es.codigoandroid.pojos.Recursos' AND META().id = $1 AND _sync IS NOT MISSING";
	        ParameterizedN1qlQuery query = ParameterizedN1qlQuery.parameterized(queryStr, JsonArray.create().add(todoId));
	        return bucket.async()
	        		.query(query)
	                .flatMap(AsyncN1qlQueryResult::rows)
	                .map(result -> result.value().toMap())
	                .filter(out -> out!=null)
	                .toList()
	                .timeout(10, TimeUnit.SECONDS)
	                .toBlocking()
	                .single()
	                .get(0);
	    }

	    public static List<Map<String, Object>> getAll(final Bucket bucket) {
	        String queryStr = "SELECT "+bucket.name()+".*, " +"META("+ bucket.name()+").id " +
	                "FROM `" + bucket.name() + "` " +
	                "WHERE documentClass = 'class es.codigoandroid.pojos.Recursos' AND _sync IS NOT MISSING";
	        return bucket.async().query(N1qlQuery.simple(queryStr))
	                .flatMap(AsyncN1qlQueryResult::rows)
	                .map(result -> result.value().toMap())
	                .doOnError(e -> System.out.println("Super mega error: " + e.getMessage() +"\n"+ e.getCause() + "\n" + e.getStackTrace() + "\n"+ e.toString()))
	                .toList()
	                .timeout(60, TimeUnit.SECONDS)
	                .toBlocking()
	                .single();
	    }
	    public static List<Map<String, Object>> getAllSenderos(final Bucket bucket) {
	        String queryStr = "SELECT "+bucket.name() +".sendero, " +"META("+ bucket.name()+").id " +
	                "FROM `" + bucket.name() + "` " +
	                "WHERE documentClass = 'class es.codigoandroid.pojos.Recursos' AND _sync IS NOT MISSING";
	        return bucket.async().query(N1qlQuery.simple(queryStr))
	                .flatMap(AsyncN1qlQueryResult::rows)
	                .map(result -> result.value().toMap())
	                .doOnError(e -> System.out.println("Super mega error: " + e.getMessage() +"\n"+ e.getCause() + "\n" + e.getStackTrace() + "\n"+ e.toString()))
	                .toList()
	                .timeout(60, TimeUnit.SECONDS)
	                .toBlocking()
	                .single();
	    }
	    
	    public static Map<String, Object> getSenderoById(final Bucket bucket, String todoId) {
	        String queryStr = "SELECT "+bucket.name()+".sendero, " + "META("+ bucket.name()+").id " + 
	                "FROM `" + bucket.name() + "` " +
	                "WHERE documentClass = 'class es.codigoandroid.pojos.Recursos' AND META().id = $1 AND _sync IS NOT MISSING";
	        ParameterizedN1qlQuery query = ParameterizedN1qlQuery.parameterized(queryStr, JsonArray.create().add(todoId));
	        return bucket.async().query(query)
	                .flatMap(AsyncN1qlQueryResult::rows)
	                .map(result -> result.value().toMap())
	                .toList()
	                .timeout(10, TimeUnit.SECONDS)
	                .toBlocking()
	                .single().get(0);
	    }
	    
	    public static Map<String, Object> getNombreSenderoById(final Bucket bucket, String todoId,String nombreId) {
	        String queryStr = "SELECT s.* ," + " META().id as RecursoId " +
	                "FROM `" + bucket.name() + "` t UNNEST t.sendero s " +
	                "WHERE t.documentClass = 'class es.codigoandroid.pojos.Recursos' AND META().id = $1 AND s.nombre = $2 AND t._sync IS NOT MISSING";
	        System.out.println("Query: " + queryStr);
	        ParameterizedN1qlQuery query = ParameterizedN1qlQuery.parameterized(queryStr, JsonArray.create().add(todoId).add(nombreId));
	        return bucket.async().query(query)
	                .flatMap(AsyncN1qlQueryResult::rows)
	                .map(result -> result.value().toMap())
	                .toList()
	                .timeout(10, TimeUnit.SECONDS)
	                .toBlocking()
	                .single().get(0);
	    }
}
