/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pure;

import java.sql.SQLException;

import org.apache.logging.log4j.Logger;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.logic.Filter;
import org.dspace.content.logic.FilterUtils;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.identifier.DOI;
import org.dspace.identifier.DOIIdentifierProvider;
import org.dspace.identifier.IdentifierException;
import org.dspace.identifier.IdentifierNotApplicableException;
import org.dspace.identifier.factory.IdentifierServiceFactory;
import org.dspace.identifier.service.DOIService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.dspace.workflow.factory.WorkflowServiceFactory;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Bundle;
import org.dspace.authorize.ResourcePolicy;
import java.util.UUID;
import java.util.List;

    /**
     * @author Oriol Olivé (oriol dot olive at udg dot edu)
     * @description This consumer is used to unembargo a bundle
     */
public class BundleUnembago implements Consumer {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(BundleUnembago.class);

    ConfigurationService configurationService;

    @Override
    public void initialize() throws Exception {
        // nothing to do
        // we can ask spring to give as a properly setuped instance of
        // DOIIdentifierProvider. Doing so we don't have to configure it and
        // can load it in consume method as this is not very expensive.
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    }

    // as we use asynchronous metadata update, our updates are not very expensive.
    // so we can do everything in the consume method.
    @Override
    public void consume(Context ctx, Event event) throws Exception {
 
        if (event.getSubjectType() != Constants.BUNDLE) {
            log.warn("BundleUnembago should not have been given this kind of "
                         + "subject in an event, skipping: " + event.toString());
            return;
        }
        // get the object
        DSpaceObject dso = event.getSubject();
        Bundle bundle = (Bundle) dso;

        // get name of the bundle
        String bundleName = bundle.getName();

        // if bundle not is ORIGINAL, skip
        if (!bundleName.equals("ORIGINAL")) {
            log.warn("BundleUnembago should not have been given this kind of "
                         + "bundle, skipping: " + event.toString());
            return;
        }

        // log the metadata name
        log.info("--------------------------------");
        log.info("Event BUNDLE UNEMBAGO: ");
        log.info("Bundle Name: " + bundleName);
        log.info("--------------------------------");
        
        // get the resource policies
        List<ResourcePolicy> resourcePolicies = bundle.getResourcePolicies();

        // Check if there are any start date
        boolean hasStartDate = false;
        for (ResourcePolicy resourcePolicy : resourcePolicies) {
            if (resourcePolicy.getStartDate() != null) {
                // put null start date
                resourcePolicy.setStartDate(null);
                hasStartDate = true;
                log.info("Reset Start date");
            }
        }

        
        
    }

    @Override
    public void end(Context ctx) throws Exception {


    }

    @Override
    public void finish(Context ctx) throws Exception {
        // nothing to do
    }


}
