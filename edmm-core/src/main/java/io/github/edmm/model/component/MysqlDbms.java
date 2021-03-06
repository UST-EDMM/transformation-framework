package io.github.edmm.model.component;

import io.github.edmm.core.parser.MappingEntity;
import io.github.edmm.model.support.Attribute;
import io.github.edmm.model.visitor.ComponentVisitor;

import lombok.ToString;

@ToString
public class MysqlDbms extends Dbms {

    public static final Attribute<String> VERSION = new Attribute<>("version", String.class);

    public MysqlDbms(MappingEntity mappingEntity) {
        super(mappingEntity);
    }

    public String getVersion() {
        return getProperty(VERSION, "8");
    }

    @Override
    public void accept(ComponentVisitor v) {
        v.visit(this);
    }
}
