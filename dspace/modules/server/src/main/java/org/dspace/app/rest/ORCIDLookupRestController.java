/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.DSpaceObjectRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.DSpaceObjectUtils;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.TemplateVariable.VariableType;
import org.springframework.hateoas.TemplateVariables;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.apache.solr.client.solrj.SolrServerException;
//import org.dspace.authority.AuthoritySolrServiceImpl;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
//import org.dspace.authority.AuthoritySearchService;
//import org.dspace.content.authority.SolrAuthority;
import org.dspace.app.rest.DiscoverableEndpointsService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * This is an utility endpoint to lookup if an authority id is .
 *
 * @author Oriol Olivé (oriol.olive at udg.edu)
 */
@RestController
@RequestMapping("/api/" + ORCIDLookupRestController.CATEGORY)
public class ORCIDLookupRestController implements InitializingBean {
    public static final String CATEGORY = "orcid";

    public static final String ACTION = "find";

    public static final String PARAM = "uuid";


    //private final AuthoritySearchService solrAuthorityCore = SolrAuthority.getSearchService();
    //private final AuthoritySolrServiceImpl solrAuthorityCore = AuthoritySolrServiceImpl();

    @Autowired
    private DSpaceObjectUtils dspaceObjectUtil;

    @Autowired
    private Utils utils;

    private static final Logger log = LogManager.getLogger();


    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;

    @Autowired
    private ConverterService converter;
    
    @Autowired
    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    
    public String urlString = configurationService.getProperty("solr.authority.server", "http://localhost:8983/solr/authority");
    private SolrClient authCore = new HttpSolrClient.Builder(urlString).build();

    


    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("ORCIDLookup: Creat entry point ORCID Lookup");
        

        discoverableEndpointsService
            .register(this,
                    Arrays.asList(
                            Link.of(
                                    UriTemplate.of("/api/" + CATEGORY + "/" + ACTION,
                                            new TemplateVariables(
                                                    new TemplateVariable(PARAM, VariableType.REQUEST_PARAM))),
                                    CATEGORY)));
    }

    @RequestMapping(method = RequestMethod.GET, value = ACTION, params = PARAM)
    @SuppressWarnings("unchecked")
    public void getORCIDbyIdentifier(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @RequestParam(PARAM) UUID uuid)
            throws IOException, SQLException, SearchServiceException {

        Context context = null;
        log.info("ORCIDLookup: get!");
        try {
            context = ContextUtil.obtainContext(request);
            // check uuid has correct pattern
            // TODO

            SolrQuery solrQuery = new SolrQuery("authority_type:\"orcid\" and id:"+uuid);

            //String urlString = "http://localhost:8983/solr/authority";
            // get from config
//            ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
//            String urlString = configurationService.getProperty("solr.authority.server");
//            SolrClient authCore = new HttpSolrClient.Builder(urlString).build();
            
            QueryResponse queryResponse = this.authCore.query(solrQuery);
            SolrDocumentList responseList = queryResponse.getResults();

            if (responseList.size() >= 1) {
                String orcid = responseList.get(0).get("orcid_id").toString();
                // todo: create best json object
                String obj = "{\"id\":\""+uuid+"\",\"orcid_id\":\""+orcid+"\"}";
//                response.setStatus(HttpServletResponse.SC_FOUND);
                // 200 insted of 302
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/json");
                response.setContentLength(obj.length());
                response.getWriter().write(obj);
                return;


            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
/*
        } catch (SolrServerException e) {
//            e.printStackTrace();
            throw new SolrServerException(e.getMessage());
*/
        } catch (Exception e) {
            e.printStackTrace();
            log.error("ORCIDLookup: ERROR: "+e.getMessage());
    
        } finally {
            context.abort();
        }
    }
}
