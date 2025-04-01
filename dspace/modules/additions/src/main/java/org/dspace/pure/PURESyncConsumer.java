/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
//package org.dspace.event;
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

// import json
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

/**
 * @author Pascal-Nicolas Becker (p dot becker at tu hyphen berlin dot de)
 * @author Kim Shepherd
 */
public class PURESyncConsumer implements Consumer {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(PURESyncConsumer.class);

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
        if (event.getSubjectType() != Constants.ITEM) {
            log.warn("PURESyncConsumer should not have been given this kind of "
                         + "subject in an event, skipping: " + event.toString());
            return;
        }
        if (Event.MODIFY_METADATA != event.getEventType()) {
            log.warn("PURESyncConsumer should not have been given this kind of "
                         + "event type, skipping: " + event.toString());
            return;
        }

        DSpaceObject dso = event.getSubject(ctx);
        //FIXME
        if (!(dso instanceof Item)) {
            log.debug("PURESyncConsumer got an event whose subject was not an item, "
                          + "skipping: " + event.toString());
            return;
        }
        Item item = (Item) dso;
        //DOIIdentifierProvider provider = new DSpace().getSingletonService(DOIIdentifierProvider.class);
        boolean inProgress = (ContentServiceFactory.getInstance().getWorkspaceItemService().findByItem(ctx, item)
                != null || WorkflowServiceFactory.getInstance().getWorkflowItemService().findByItem(ctx, item) != null);

        // get the PURE API URL
        String pureApiUrl = configurationService.getProperty("pure.api.url");
        if (pureApiUrl == null) {
            log.warn("PURESyncConsumer cannot get PURE API URL, skipping: " + event.toString());
            return;
        }
        // get the PURE API key
        String pureApiKey = configurationService.getProperty("pure.api.key");
        if (pureApiKey == null) {
            log.warn("PURESyncConsumer cannot get PURE API key, skipping: " + event.toString());
            return;
        }

        // get metadata name from pure id
        String metadataName = configurationService.getProperty("pure.metadata.name");
        if (metadataName == null) {
            // default to "pure_id"
            metadataName = "dc.identifier.gerioid";
        }

        // need to get the item pure id from the item
        String itemPureId = item.getMetadata(metadataName)[0];

        if (itemPureId == null) {
            // item does not have a PURE ID, so we cannot sync it or it is not from PURE
            return;
        }
        // get item from pure
        try {
            String itemJsonString = getItemFromPure(itemPureId, pureApiUrl);
        } catch (Exception e) {
            log.warn("PURESyncConsumer cannot get item from Pure, skipping: " + event.toString());
            return;
        }
        // get the item handle
        String itemHandle = item.getMetadata("dc.identifier.uri")[0];
        // get json part
        JSONObject itemJson = new JSONObject(itemJsonString);

        // get electronicVersions part
        JSONObject electronicVersions = itemJson.getJSONArray("electronicVersions").getJSONObject(0);

        // retrieve accessType and versionType from the first electronicVersions object
        JSONObject accessType = electronicVersions.getJSONObject("accessType");
        JSONObject versionType = electronicVersions.getJSONObject("versionType");

        // create a link object inside the item json with the specified shape
        JSONObject link = new JSONObject();
        link.put("typeDiscriminator", "LinkElectronicVersion");
        link.put("accessType", accessType);
        link.put("link", itemHandle);
        link.put("versionType", versionType);
        itemJson.append("electronicVersions", link); // Append the link object to the electronicVersions array

 
        try {
            // update item with Pure item json
            Response response = updateItemWithPure(itemPureId, itemJson.toString(), pureApiUrl);
        } catch (Exception e) {
            log.warn("PURESyncConsumer cannot update item with Pure, skipping: " + event.toString());
            return;
        }


    }

    @Override
    public void end(Context ctx) throws Exception {


    }

    @Override
    public void finish(Context ctx) throws Exception {
        // nothing to do
    }

    /*
     * Get item from Pure
     */
    private String getItemFromPure(String itemPureId, String pureApiUrl) throws Exception {
        // get item from Pure
        // Constructing the URL for the API endpoint
        String url = String.format("https://%s/ws/api/research-outputs/%s", pureApiUrl, itemPureId);
        
        // Creating the headers for the request
        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", pureToken);
        
        // Making the GET request to retrieve the item from Pure
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        // Checking if the response is successful
        if (response.getStatusCode() == HttpStatus.OK) {
            String itemJson = response.getBody();
        } else {
            throw new Exception("Failed to retrieve item from Pure: " + response.getStatusCode());
        }
        return itemJson;
    }

    /*
     * Update item with Pure
     */
    private String updateItemWithPure(String itemPureId, String itemJson, String pureApiUrl) throws Exception {
        // Making a POST request to update the item with Pure
        String url = String.format("https://%s/ws/api/research-outputs/%s", pureApiUrl, itemPureId);
        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", pureToken);
        HttpEntity<String> requestEntity = new HttpEntity<>(itemJson, headers);
        
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        
        // Check if the response is successful
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            throw new Exception("Failed to update item with Pure: " + responseEntity.getStatusCode());
        }
    }
}
