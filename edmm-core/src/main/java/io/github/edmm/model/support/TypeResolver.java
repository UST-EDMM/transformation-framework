package io.github.edmm.model.support;

import java.util.Set;

import io.github.edmm.model.component.Auth0;
import io.github.edmm.model.component.AwsAurora;
import io.github.edmm.model.component.AwsBeanstalk;
import io.github.edmm.model.component.Compute;
import io.github.edmm.model.component.Database;
import io.github.edmm.model.component.Dbaas;
import io.github.edmm.model.component.Dbms;
import io.github.edmm.model.component.Go;
import io.github.edmm.model.component.Mom;
import io.github.edmm.model.component.MongoDb;
import io.github.edmm.model.component.MongoDbSchema;
import io.github.edmm.model.component.MysqlDatabase;
import io.github.edmm.model.component.MysqlDbms;
import io.github.edmm.model.component.Paas;
import io.github.edmm.model.component.Platform;
import io.github.edmm.model.component.RabbitMq;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.component.Saas;
import io.github.edmm.model.component.SoftwareComponent;
import io.github.edmm.model.component.Tomcat;
import io.github.edmm.model.component.WebApplication;
import io.github.edmm.model.component.WebServer;
import io.github.edmm.model.relation.ConnectsTo;
import io.github.edmm.model.relation.DependsOn;
import io.github.edmm.model.relation.HostedOn;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TypeResolver {

    private static final Logger logger = LoggerFactory.getLogger(TypeResolver.class);

    private static final BidiMap<String, Class<? extends ModelEntity>> TYPE_MAPPING = new DualHashBidiMap<>();

    static {
        // Components
        put("base", RootComponent.class);
        put("compute", Compute.class);
        put("software_component", SoftwareComponent.class);
        put("web_server", WebServer.class);
        put("web_application", WebApplication.class);
        put("dbms", Dbms.class);
        put("database", Database.class);
        put("tomcat", Tomcat.class);
        put("mysql_dbms", MysqlDbms.class);
        put("mysql_database", MysqlDatabase.class);
        put("platform", Platform.class);
        put("paas", Paas.class);
        put("dbaas", Dbaas.class);
        put("aws_beanstalk", AwsBeanstalk.class);
        put("aws_aurora", AwsAurora.class);
        put("saas", Saas.class);
        put("auth0", Auth0.class);
        put("go", Go.class);
        put("mom", Mom.class);
        put("rabbitmq", RabbitMq.class);
        put("mongodb", MongoDb.class);
        put("mongodb_schema", MongoDbSchema.class);

        // Relations
        put("depends_on", DependsOn.class);
        put("hosted_on", HostedOn.class);
        put("connects_to", ConnectsTo.class);
    }

    public static Class<? extends ModelEntity> resolve(String type) {
        Class<? extends ModelEntity> clazz = TYPE_MAPPING.get(type);
        if (clazz != null) {
            return clazz;
        } else {
            logger.warn("Type '{}' is unknown and not supported", type);
            return RootComponent.class;
        }
    }

    public static String resolve(Class<? extends ModelEntity> clazz) {
        String type = TYPE_MAPPING.getKey(clazz);
        if (type != null) {
            return type;
        } else {
            logger.warn("Class '{}' is unknown and not supported", clazz.getName());
            return TYPE_MAPPING.getKey(RootComponent.class);
        }
    }

    public static Set<String> typeSet() {
        return TYPE_MAPPING.keySet();
    }

    private static void put(String name, Class<? extends ModelEntity> clazz) {
        TYPE_MAPPING.put(name, clazz);
    }
}
