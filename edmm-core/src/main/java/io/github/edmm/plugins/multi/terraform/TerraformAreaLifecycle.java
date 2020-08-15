package io.github.edmm.plugins.multi.terraform;

import io.github.edmm.core.plugin.AbstractLifecycle;
import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.model.component.Compute;
import io.github.edmm.model.component.Platform;
import io.github.edmm.model.visitor.VisitorHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraformAreaLifecycle extends AbstractLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(TerraformAreaLifecycle.class);


    public TerraformAreaLifecycle(TransformationContext context) {
        super(context);
    }

    public void prepare() {

    }

    public void transform() {
        logger.info("Begin transformation to Terraform...");
        TerraformVisitor visitor = new TerraformVisitor(context);
        // Visit compute components first
        VisitorHelper.visit(context.getGroup().getGroupComponents(), visitor, component -> component instanceof Compute || component instanceof Platform);
        // ... then all others
        VisitorHelper.visit(context.getGroup().getGroupComponents(), visitor);
        //VisitorHelper.visit(context.getModel().getRelations(), visitor);
        visitor.populate();
        logger.info("Transformation to Terraform successful");
    }

    @Override
    public void cleanup() {

    }
}
