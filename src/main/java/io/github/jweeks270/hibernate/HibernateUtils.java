package io.github.jweeks270.hibernate;

import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;

import io.github.jweeks270.string.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A hibernate utility class that allows you to run queries that go slightly outside the lines
 */
public class HibernateUtils {
    private HibernateUtils() {
        throw new UnsupportedOperationException("This is a static utility file, and cannot be instantiated.");
    }

    /**
	 * Advantages:  Complete native SQL autonomy.  You can write out your own 
     * native SQL, and map it as you see fit
     * 
     * Disadvantages:  It is very name picky.  Names have to match exactly, or it will fail
     * 
     * This method is for getting the result of a dynamic query to a list 
     * of defined POJOs.  No mapper is necessary, the only requirement is 
     * that the output aliases of the query have to match the names of 
     * the POJO it's being mapped to.  You can map it by the returned 
     * column names, or you can use "AS" to tell it a variable name to 
     * map out to.  
     * 
     * Variable names and column names cannot be duplicated in the 
     * output or the POJO, or the column alias of the query
     * IE - Variable name is the same as variable NAME and nAmE
     * 
     * @param <T> a param type
	 * @param entityManager An entity manager object passed to the method
	 * @param querySb An ArrayList of Strings to assemble a dynamic query
	 * @param queryParams A String, Object Map to contain the bind variables for the assembled query
     * @param klazz The output class to map results to
	 *
     * @throws ClassCastException If there isn't a name matching that can be mapped in the result, or there's an issue creating an instance of the mapping POJO class, a ClassCastException will be thrown
     * 
	 * @return A List of mapped POJO's from the input query
	*/
    @SuppressWarnings("unchecked")
    public static <T> List<T> createNativePojoProjection(EntityManager entityManager, List<String> querySb, Map<String, Object> queryParams, Class<T> klazz) {
        @SuppressWarnings("java:S1854")
        List<T> resultList = new ArrayList<>();
        Query query = entityManager.createNativeQuery(createQueryString(querySb));
        if (!queryParams.isEmpty()) {
            queryParams.forEach(query::setParameter);
        }

        List<Field> fieldList = Arrays.asList(klazz.getDeclaredFields());

        try {
            query.unwrap(org.hibernate.query.Query.class)
                .setTupleTransformer(
                    (tuple, aliases) -> {
                        T pr;
                        try {
                            pr = klazz.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new ClassCastException();
                        }
                        
                        AtomicInteger count = new AtomicInteger(0);
                        Arrays.asList(aliases).forEach(alias -> {
                            String comp = StringUtils.convertToCamelCase(alias, '_');

                            Field fieldToSet = fieldList.stream().filter(i -> i.getName().equalsIgnoreCase(comp)).findFirst().orElse(null);

                            PropertyAccessor myAccessor = PropertyAccessorFactory.forBeanPropertyAccess(pr);
                            try {
                                myAccessor.setPropertyValue(fieldToSet.getName(), tuple[count.get()]);
                            } catch (Exception ex) {
                                // Fail silently, and move on to map the next field
                            }
                            
                            count.getAndIncrement();
                        });

                        return pr;
                    }
                );
            resultList = query.getResultList();
        } catch (Exception ex) {
            throw new ClassCastException("Could not map result because: " + ex.getMessage());
        }

        entityManager.close();
        return resultList;
    }

    /**
     * 
     * This method is to run any sql query that has no return natively
	 *
     * @param entityManager An entity manager object passed to the method
	 * @param querySb An ArrayList of Strings to assemble a dynamic query
     * @param bindVars A String, Object Map to contain the bind variables for the assembled query
	 *
     * @throws ClassCastException If there isn't a name matching that can be mapped in the result, or there's an issue creating an instance of the mapping POJO class, a ClassCastException will be thrown
	*/
    public static void executeNoReturnQuery(EntityManager entityManager, List<String> querySb, Map<String, Object> bindVars) {
        String queryString = createQueryString(querySb);
        Query query = entityManager.createNativeQuery(queryString);
        bindVars.forEach(query::setParameter);
        query.executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * This query simply concatenates a prepared query string
     * @param querySb An ArrayList of query pieces
     * @return A complete query string
     */
    public static String createQueryString(List<String> querySb) {
        StringBuilder sb = new StringBuilder();
        querySb.forEach(i -> sb.append(i).append("\n"));
        return sb.toString();
    }
}
