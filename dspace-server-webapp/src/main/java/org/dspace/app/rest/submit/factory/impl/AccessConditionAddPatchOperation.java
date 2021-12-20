/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.factory.impl;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.AccessConditionDTO;
import org.dspace.app.rest.model.patch.LateObjectEvaluator;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.submit.model.AccessConditionConfiguration;
import org.dspace.submit.model.AccessConditionConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Submission "add" operation to add custom resource policies.
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class AccessConditionAddPatchOperation extends AddPatchOperation<AccessConditionDTO> {

    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private ResourcePolicyService resourcePolicyService;
    @Autowired
    private AccessConditionConfigurationService accessConditionConfigurationService;

    @Override
    void add(Context context, HttpServletRequest currentRequest, InProgressSubmission source, String path, Object value)
        throws Exception {

        String stepId = (String) currentRequest.getAttribute("accessConditionSectionId");
        AccessConditionConfiguration configuration = accessConditionConfigurationService.getMap().get(stepId);

        Item item = source.getItem();

        //"path": "/sections/<:name-of-the-form>/accessConditions/-"
        String[] split = getAbsolutePath(path).split("/");
        List<AccessConditionDTO> accessConditions = parseAccessConditions(path, value, split);

        verifyAccessConditions(context, configuration, accessConditions);

        if (split.length == 1) {
            // to replace completely the access conditions
            authorizeService.removePoliciesActionFilter(context, item, Constants.READ);
        }
        if (split.length == 2) {
            // check duplicate policy
            checkDuplication(context, item, accessConditions);
        }

        // apply policies
        AccessConditionResourcePolicyUtils.findApplyResourcePolicy(context, configuration.getOptions(), item,
                accessConditions);
    }

    private List<AccessConditionDTO> parseAccessConditions(String path, Object value, String[] split) {
        List<AccessConditionDTO> accessConditions = new ArrayList<AccessConditionDTO>();
        if (split.length == 1) {
            accessConditions = evaluateArrayObject((LateObjectEvaluator) value);
        } else if (split.length == 2) {
            accessConditions.add(evaluateSingleObject((LateObjectEvaluator) value));
        } else {
            throw new UnprocessableEntityException("The patch operation for path:" + path + " is not supported!");
        }
        return accessConditions;
    }

    private void verifyAccessConditions(Context context, AccessConditionConfiguration configuration,
            List<AccessConditionDTO> accessConditions) throws SQLException, AuthorizeException, ParseException {
        for (AccessConditionDTO dto : accessConditions) {
            AccessConditionResourcePolicyUtils.canApplyResourcePolicy(context, configuration.getOptions(),
                    dto.getName(), dto.getStartDate(), dto.getEndDate());
        }
    }

    private void checkDuplication(Context context, Item item, List<AccessConditionDTO> accessConditions)
            throws SQLException {
        List<ResourcePolicy> policies = resourcePolicyService.find(context, item, ResourcePolicy.TYPE_CUSTOM);
        for (ResourcePolicy resourcePolicy : policies) {
            for (AccessConditionDTO dto : accessConditions) {
                if (dto.getName().equals(resourcePolicy.getRpName())) {
                    throw new UnprocessableEntityException("A policy of the same type already exists!");
                }
            }
        }
    }

    @Override
    protected Class<AccessConditionDTO[]> getArrayClassForEvaluation() {
        return AccessConditionDTO[].class;
    }

    @Override
    protected Class<AccessConditionDTO> getClassForEvaluation() {
        return AccessConditionDTO.class;
    }

}