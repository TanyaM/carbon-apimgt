/*
*  Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.apimgt.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.wso2.carbon.apimgt.api.APIAdmin;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtResourceNotFoundException;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.api.dto.KeyManagerConfigurationDTO;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APICategory;
import org.wso2.carbon.apimgt.api.model.Application;
import org.wso2.carbon.apimgt.api.model.ConfigurationDto;
import org.wso2.carbon.apimgt.api.model.KeyManagerConnectorConfiguration;
import org.wso2.carbon.apimgt.api.model.Label;
import org.wso2.carbon.apimgt.api.model.Monetization;
import org.wso2.carbon.apimgt.api.model.MonetizationUsagePublishInfo;
import org.wso2.carbon.apimgt.api.model.Workflow;
import org.wso2.carbon.apimgt.api.model.botDataAPI.BotDetectionData;
import org.wso2.carbon.apimgt.impl.alertmgt.AlertMgtConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dao.constants.SQLConstants;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.keymgt.KeyMgtNotificationSender;
import org.wso2.carbon.apimgt.impl.monetization.DefaultMonetizationImpl;
import org.wso2.carbon.apimgt.impl.service.KeyMgtRegistrationService;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This class provides the core API admin functionality.
 */
public class APIAdminImpl implements APIAdmin {

    private static final Log log = LogFactory.getLog(APIAdminImpl.class);
    protected ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();

    /**
     * Returns all labels associated with given tenant domain.
     *
     * @param tenantDomain tenant domain
     * @return List<Label>  List of label of given tenant domain.
     * @throws APIManagementException
     */
    public List<Label> getAllLabels(String tenantDomain) throws APIManagementException {

        return apiMgtDAO.getAllLabels(tenantDomain);
    }

    /**
     * Creates a new label for the tenant
     *
     * @param tenantDomain    tenant domain
     * @param label           content to add
     * @throws APIManagementException if failed add Label
     */
    public Label addLabel(String tenantDomain, Label label) throws APIManagementException {

        if (isLableNameExists(tenantDomain, label)) {
            APIUtil.handleException("Label with name " + label.getName() + " already exists");
        }
        return apiMgtDAO.addLabel(tenantDomain, label);
    }

    /**
     * Delete an existing label
     *
     * @param labelId Label identifier
     * @throws APIManagementException If failed to delete label
     */
    public void deleteLabel(String user, String labelId) throws APIManagementException {

        if (isAttachedLabel(user, labelId)) {
            APIUtil.handleException("Unable to delete the label. It is attached to an API");
        }
        apiMgtDAO.deleteLabel(labelId);
    }

    /**
     * Updates the details of the given Label.
     *
     * @param label             content to update
     * @throws APIManagementException if failed to update label
     */
    public Label updateLabel(String tenantDomain, Label label) throws APIManagementException {

        return apiMgtDAO.updateLabel(label);
    }

    /**
     *
     * @param label content to check
     * @return whether label is already added or not
     * @throws APIManagementException
     */
    public boolean isLableNameExists(String tenantDomain, Label label) throws APIManagementException {

        List<Label> ExistingLables = apiMgtDAO.getAllLabels(tenantDomain);
        for (Label labels : ExistingLables) {
            if (labels.getName().equalsIgnoreCase(label.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAttachedLabel(String user, String labelId) throws APIManagementException {

        APIProviderImpl apiProvider = new APIProviderImpl(user);
        List<API> apiList = apiProvider.getAllAPIs();
        List<Label> allLabelsWithID = getAllLabels(MultitenantUtils.getTenantDomain(user));
        String labelName = null;
        for (Label label : allLabelsWithID) {
            if (labelId.equalsIgnoreCase(label.getLabelId())) {
                labelName = label.getName();
                break;
            }
        }
        if (labelName != null && !StringUtils.isEmpty(labelName)) {
            UserAwareAPIProvider userAwareAPIProvider = new UserAwareAPIProvider(user);
            for (API api : apiList) {
                String uuid = api.getUUID();
                API lightweightAPIByUUID = userAwareAPIProvider.getLightweightAPIByUUID(uuid, apiProvider.
                        tenantDomain);
                List<Label> attachedLabelsWithoutID = lightweightAPIByUUID.getGatewayLabels();
                for (Label labelWithoutId : attachedLabelsWithoutID) {
                    if (labelName.equalsIgnoreCase(labelWithoutId.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Application[] getAllApplicationsOfTenantForMigration(String appTenantDomain) throws APIManagementException {

        return apiMgtDAO.getAllApplicationsOfTenantForMigration(appTenantDomain);
    }

    /**
     * Get applications for the tenantId.
     *
     * @param tenantId             tenant Id
     * @param start                content to start
     * @param offset               content to limit number of pages
     * @param searchOwner          content to search applications based on owners
     * @param searchApplication    content to search applications based on application
     * @param sortColumn           content to sort column
     * @param sortOrder            content to sort in a order
     * @throws APIManagementException if failed to get application
     */
    public List<Application> getApplicationsByTenantIdWithPagination(int tenantId, int start, int offset
            , String searchOwner, String searchApplication, String sortColumn, String sortOrder)
            throws APIManagementException {

        return apiMgtDAO.getApplicationsByTenantIdWithPagination(tenantId, start, offset,
                searchOwner, searchApplication, sortColumn, sortOrder);
    }

    /**
     * Get count of the applications for the tenantId.
     *
     * @param tenantId             content to get application count based on tenant_id
     * @param searchOwner          content to search applications based on owners
     * @param searchApplication    content to search applications based on application
     * @throws APIManagementException if failed to get application
     */

    public int getApplicationsCount(int tenantId, String searchOwner, String searchApplication)
            throws APIManagementException {

        return apiMgtDAO.getApplicationsCount(tenantId, searchOwner, searchApplication);
    }

    /**
     * This methods loads the monetization implementation class
     *
     * @return monetization implementation class
     * @throws APIManagementException if failed to load monetization implementation class
     */
    public Monetization getMonetizationImplClass() throws APIManagementException {

        APIManagerConfiguration configuration = org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder.
                getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration();
        Monetization monetizationImpl = null;
        if (configuration == null) {
            log.error("API Manager configuration is not initialized.");
        } else {
            String monetizationImplClass = configuration.getFirstProperty(APIConstants.Monetization.MONETIZATION_IMPL);
            if (monetizationImplClass == null) {
                monetizationImpl = new DefaultMonetizationImpl();
            } else {
                try {
                    monetizationImpl = (Monetization) APIUtil.getClassForName(monetizationImplClass).newInstance();
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                    APIUtil.handleException("Failed to load monetization implementation class.", e);
                }
            }
        }
        return monetizationImpl;
    }

    /**
     * Derives info about monetization usage publish job
     *
     * @return ifno about the monetization usage publish job
     * @throws APIManagementException
     */
    public MonetizationUsagePublishInfo getMonetizationUsagePublishInfo() throws APIManagementException {

        return apiMgtDAO.getMonetizationUsagePublishInfo();
    }

    /**
     * Updates info about monetization usage publish job
     *
     * @throws APIManagementException
     */
    public void updateMonetizationUsagePublishInfo(MonetizationUsagePublishInfo monetizationUsagePublishInfo)
            throws APIManagementException {

        apiMgtDAO.updateUsagePublishInfo(monetizationUsagePublishInfo);
    }

    /**
     * Add info about monetization usage publish job
     *
     * @throws APIManagementException
     */
    public void addMonetizationUsagePublishInfo(MonetizationUsagePublishInfo monetizationUsagePublishInfo)
            throws APIManagementException {

        apiMgtDAO.addMonetizationUsagePublishInfo(monetizationUsagePublishInfo);
    }

    /**
     * The method converts the date into timestamp
     *
     * @param date
     * @return Timestamp in long format
     */
    public long getTimestamp(String date) {

        SimpleDateFormat formatter = new SimpleDateFormat(APIConstants.Monetization.USAGE_PUBLISH_TIME_FORMAT);
        formatter.setTimeZone(TimeZone.getTimeZone(APIConstants.Monetization.USAGE_PUBLISH_TIME_ZONE));
        long time = 0;
        Date parsedDate = null;
        try {
            parsedDate = formatter.parse(date);
            time = parsedDate.getTime();
        } catch (java.text.ParseException e) {
            log.error("Error while parsing the date ", e);
        }
        return time;
    }

    @Override
    public List<KeyManagerConfigurationDTO> getKeyManagerConfigurationsByTenant(String tenantDomain)
            throws APIManagementException {

        KeyMgtRegistrationService.registerDefaultKeyManager(tenantDomain);
        List<KeyManagerConfigurationDTO> keyManagerConfigurationsByTenant =
                apiMgtDAO.getKeyManagerConfigurationsByTenant(tenantDomain);
        Iterator<KeyManagerConfigurationDTO> iterator = keyManagerConfigurationsByTenant.iterator();
        KeyManagerConfigurationDTO defaultKeyManagerConfiguration = null;
        while (iterator.hasNext()) {
            KeyManagerConfigurationDTO keyManagerConfigurationDTO = iterator.next();
            if (APIConstants.KeyManager.DEFAULT_KEY_MANAGER.equals(keyManagerConfigurationDTO.getName())) {
                defaultKeyManagerConfiguration = keyManagerConfigurationDTO;
                iterator.remove();
                break;
            }
        }
        if (defaultKeyManagerConfiguration != null) {
            APIUtil.getAndSetDefaultKeyManagerConfiguration(defaultKeyManagerConfiguration);
            keyManagerConfigurationsByTenant.add(defaultKeyManagerConfiguration);
        }
        return keyManagerConfigurationsByTenant;
    }

    @Override
    public Map<String, List<KeyManagerConfigurationDTO>> getAllKeyManagerConfigurations()
            throws APIManagementException {

        List<KeyManagerConfigurationDTO> keyManagerConfigurations = apiMgtDAO.getKeyManagerConfigurations();
        Map<String, List<KeyManagerConfigurationDTO>> keyManagerConfigurationsByTenant = new HashMap<>();
        for (KeyManagerConfigurationDTO keyManagerConfiguration : keyManagerConfigurations) {
            List<KeyManagerConfigurationDTO> keyManagerConfigurationDTOS;
            if (keyManagerConfigurationsByTenant.containsKey(keyManagerConfiguration.getTenantDomain())) {
                keyManagerConfigurationDTOS =
                        keyManagerConfigurationsByTenant.get(keyManagerConfiguration.getTenantDomain());
            } else {
                keyManagerConfigurationDTOS = new ArrayList<>();
            }
            if (APIConstants.KeyManager.DEFAULT_KEY_MANAGER.equals(keyManagerConfiguration.getName())) {
                APIUtil.getAndSetDefaultKeyManagerConfiguration(keyManagerConfiguration);
            }
            keyManagerConfigurationDTOS.add(keyManagerConfiguration);
            keyManagerConfigurationsByTenant
                    .put(keyManagerConfiguration.getTenantDomain(), keyManagerConfigurationDTOS);
        }
        return keyManagerConfigurationsByTenant;
    }

    @Override
    public KeyManagerConfigurationDTO getKeyManagerConfigurationById(String tenantDomain, String id)
            throws APIManagementException {

        KeyManagerConfigurationDTO keyManagerConfigurationDTO =
                apiMgtDAO.getKeyManagerConfigurationByID(tenantDomain, id);
        if (keyManagerConfigurationDTO != null &&
                APIConstants.KeyManager.DEFAULT_KEY_MANAGER.equals(keyManagerConfigurationDTO.getName())) {
            APIUtil.getAndSetDefaultKeyManagerConfiguration(keyManagerConfigurationDTO);
        }

        return keyManagerConfigurationDTO;
    }

    @Override
    public boolean isKeyManagerConfigurationExistById(String tenantDomain, String id) throws APIManagementException {

        return apiMgtDAO.isKeyManagerConfigurationExistById(tenantDomain, id);
    }

    @Override
    public KeyManagerConfigurationDTO addKeyManagerConfiguration(KeyManagerConfigurationDTO keyManagerConfigurationDTO)
            throws APIManagementException {

        if (apiMgtDAO.isKeyManagerConfigurationExistByName(keyManagerConfigurationDTO.getName(),
                keyManagerConfigurationDTO.getTenantDomain())) {
            throw new APIManagementException(
                    "Key manager Already Exist by Name " + keyManagerConfigurationDTO.getName() + " in tenant " +
                            keyManagerConfigurationDTO.getTenantDomain(), ExceptionCodes.KEY_MANAGER_ALREADY_EXIST);
        }
        validateKeyManagerConfiguration(keyManagerConfigurationDTO);
        keyManagerConfigurationDTO.setUuid(UUID.randomUUID().toString());
        apiMgtDAO.addKeyManagerConfiguration(keyManagerConfigurationDTO);
        new KeyMgtNotificationSender()
                .notify(keyManagerConfigurationDTO, APIConstants.KeyManager.KeyManagerEvent.ACTION_ADD);
        return keyManagerConfigurationDTO;
    }

    @Override
    public KeyManagerConfigurationDTO updateKeyManagerConfiguration(
            KeyManagerConfigurationDTO keyManagerConfigurationDTO)
            throws APIManagementException {
        validateKeyManagerConfiguration(keyManagerConfigurationDTO);
        apiMgtDAO.updateKeyManagerConfiguration(keyManagerConfigurationDTO);
        new KeyMgtNotificationSender()
                .notify(keyManagerConfigurationDTO, APIConstants.KeyManager.KeyManagerEvent.ACTION_UPDATE);
        return keyManagerConfigurationDTO;
    }

    @Override
    public void deleteKeyManagerConfigurationById(String tenantDomain, String id) throws APIManagementException {

        KeyManagerConfigurationDTO keyManagerConfigurationDTO =
                apiMgtDAO.getKeyManagerConfigurationByID(tenantDomain, id);
        if (keyManagerConfigurationDTO != null) {
            apiMgtDAO.deleteKeyManagerConfigurationById(id, tenantDomain);
            new KeyMgtNotificationSender()
                    .notify(keyManagerConfigurationDTO, APIConstants.KeyManager.KeyManagerEvent.ACTION_DELETE);
        }
    }

    @Override
    public KeyManagerConfigurationDTO getKeyManagerConfigurationByName(String tenantDomain, String name)
            throws APIManagementException {

        KeyManagerConfigurationDTO keyManagerConfiguration =
                apiMgtDAO.getKeyManagerConfigurationByName(tenantDomain, name);
        if (keyManagerConfiguration != null &&
                APIConstants.KeyManager.DEFAULT_KEY_MANAGER.equals(keyManagerConfiguration.getName())) {
            APIUtil.getAndSetDefaultKeyManagerConfiguration(keyManagerConfiguration);
        }
        return keyManagerConfiguration;
    }

    @Override
    public void addBotDetectionAlertSubscription(String email) throws APIManagementException {

        apiMgtDAO.addBotDetectionAlertSubscription(email);
    }

    @Override
    public List<BotDetectionData> getBotDetectionAlertSubscriptions() throws APIManagementException {

        return apiMgtDAO.getBotDetectionAlertSubscriptions();
    }

    @Override
    public void deleteBotDetectionAlertSubscription(String uuid) throws APIManagementException {

        apiMgtDAO.deleteBotDetectionAlertSubscription(uuid);
    }

    @Override
    public BotDetectionData getBotDetectionAlertSubscription(String field, String value) throws APIManagementException {

        return apiMgtDAO.getBotDetectionAlertSubscription(field, value);
    }

    @Override
    public List<BotDetectionData> retrieveBotDetectionData() throws APIManagementException {

        List<BotDetectionData> botDetectionDatalist = new ArrayList<>();
        String appName = AlertMgtConstants.APIM_ALERT_BOT_DETECTION_APP;
        String query = SQLConstants.BotDataConstants.GET_BOT_DETECTED_DATA;

        JSONObject botDataJsonObject = APIUtil.executeQueryOnStreamProcessor(appName, query);
        if (botDataJsonObject != null) {
            JSONArray botDataJsonArray = (JSONArray) botDataJsonObject.get("records");
            if (botDataJsonArray != null && botDataJsonArray.size() != 0) {
                for (Object botData : botDataJsonArray) {
                    JSONArray values = (JSONArray) botData;
                    BotDetectionData botDetectionData = new BotDetectionData();
                    botDetectionData.setCurrentTime((Long) values.get(0));
                    botDetectionData.setMessageID((String) values.get(1));
                    botDetectionData.setApiMethod((String) values.get(2));
                    botDetectionData.setHeaderSet((String) values.get(3));
                    botDetectionData.setMessageBody(extractBotDetectionDataContent((String) values.get(4)));
                    botDetectionData.setClientIp((String) values.get(5));
                    botDetectionDatalist.add(botDetectionData);
                }
            }
        }
        return botDetectionDatalist;
    }

    /**
     * Extract content of the bot detection data
     *
     * @param messageBody message body of bot detection data
     * @return extracted content
     */
    public String extractBotDetectionDataContent(String messageBody) {

        String content;
        try {
            //Parse the message body and extract the content in XML form
            DocumentBuilderFactory factory = APIUtil.getSecuredDocumentBuilder();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(messageBody)));
            Node bodyContentNode = document.getFirstChild().getFirstChild();

            //Convert XML form to String
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(bodyContentNode), new StreamResult(writer));
            String output = writer.toString();
            content = output.substring(output.indexOf("?>") + 2); //remove <?xml version="1.0" encoding="UTF-8"?>
        } catch (ParserConfigurationException | TransformerException | IOException | SAXException e) {
            String errorMessage = "Error while extracting content from " + messageBody;
            log.error(errorMessage, e);
            content = messageBody;
        }
        return content;
    }

    public APICategory addCategory(APICategory category, String userName) throws APIManagementException {

        int tenantID = APIUtil.getTenantId(userName);
        if (isCategoryNameExists(category.getName(), null, tenantID)) {
            APIUtil.handleException("Category with name '" + category.getName() + "' already exists");
        }
        return apiMgtDAO.addCategory(tenantID, category);
    }

    public void updateCategory(APICategory apiCategory) throws APIManagementException {

        apiMgtDAO.updateCategory(apiCategory);
    }

    public void deleteCategory(String categoryID, String username) throws APIManagementException {

        APICategory category = getAPICategoryByID(categoryID);
        int attchedAPICount = isCategoryAttached(category, username);
        if (attchedAPICount > 0) {
            APIUtil.handleException("Unable to delete the category. It is attached to API(s)");
        }
        apiMgtDAO.deleteCategory(categoryID);
    }

    public List<APICategory> getAllAPICategoriesOfTenant(int tenantId) throws APIManagementException {

        return apiMgtDAO.getAllCategories(tenantId);
    }

    public List<APICategory> getAPICategoriesOfTenant(int tenantId) throws APIManagementException {
        String username = CarbonContext.getThreadLocalCarbonContext().getUsername();
        List<APICategory> categories = getAllAPICategoriesOfTenant(tenantId);
        if (categories.size() > 0) {
            for (APICategory category : categories) {
                int length = isCategoryAttached(category, username);
                category.setNumberOfAPIs(length);
            }
        }
        return categories;
    }

    public List<APICategory> getAllAPICategoriesOfTenantForAdminListing(String username) throws APIManagementException {
        int tenantID = APIUtil.getTenantId(username);
        List<APICategory> categories = getAllAPICategoriesOfTenant(tenantID);
        if (categories.size() > 0) {
            for (APICategory category : categories) {
                int length = isCategoryAttached(category, username);
                category.setNumberOfAPIs(length);
            }
        }
        return categories;
    }

    public boolean isCategoryNameExists(String categoryName, String uuid, int tenantID) throws APIManagementException {

        return apiMgtDAO.isAPICategoryNameExists(categoryName, uuid, tenantID);
    }

    public APICategory getAPICategoryByID(String apiCategoryId) throws APIManagementException {

        APICategory apiCategory = apiMgtDAO.getAPICategoryByID(apiCategoryId);
        if (apiCategory != null) {
            return apiCategory;
        } else {
            String msg = "Failed to get APICategory. API category corresponding to UUID " + apiCategoryId
                    + " does not exist";
            throw new APIMgtResourceNotFoundException(msg);
        }
    }

    private int isCategoryAttached(APICategory category, String username) throws APIManagementException {

        APIProviderImpl apiProvider = new APIProviderImpl(username);
        //no need to add type prefix here since we need to ge the total number of category associations including both
        //APIs and API categories
        String searchQuery = APIConstants.CATEGORY_SEARCH_TYPE_PREFIX + "=*" + category.getName() + "*";
        String tenantDomain = MultitenantUtils.getTenantDomain(username);
        Map<String, Object> result = apiProvider
                .searchPaginatedAPIs(searchQuery, tenantDomain, 0, Integer.MAX_VALUE, true);
        return (int) (Integer) result.get("length");
    }

    private void validateKeyManagerConfiguration(KeyManagerConfigurationDTO keyManagerConfigurationDTO)
            throws APIManagementException {

        if (StringUtils.isEmpty(keyManagerConfigurationDTO.getName())) {
            throw new APIManagementException("Key Manager Name can't be empty", ExceptionCodes.KEY_MANAGER_NAME_EMPTY);
        }
        if (!APIConstants.KeyManager.DEFAULT_KEY_MANAGER_TYPE.equals(keyManagerConfigurationDTO.getType())) {
            KeyManagerConnectorConfiguration keyManagerConnectorConfiguration = ServiceReferenceHolder.getInstance()
                    .getKeyManagerConnectorConfiguration(keyManagerConfigurationDTO.getType());
            if (keyManagerConnectorConfiguration != null) {
                List<String> missingRequiredConfigurations = new ArrayList<>();
                for (ConfigurationDto configurationDto : keyManagerConnectorConfiguration
                        .getConnectionConfigurations()) {
                    if (configurationDto.isRequired()) {
                        if (!keyManagerConfigurationDTO.getAdditionalProperties()
                                .containsKey(configurationDto.getName())) {
                            if (StringUtils.isNotEmpty(configurationDto.getDefaultValue())) {
                                keyManagerConfigurationDTO.getAdditionalProperties().put(configurationDto.getName(),
                                        configurationDto.getDefaultValue());
                            }
                            missingRequiredConfigurations.add(configurationDto.getName());
                        }
                    }
                }
                if (!missingRequiredConfigurations.isEmpty()) {
                    throw new APIManagementException("Key Manager Configuration value for " + String.join(",",
                            missingRequiredConfigurations) + " is/are required",
                            ExceptionCodes.REQUIRED_KEY_MANAGER_CONFIGURATION_MISSING);
                }
            } else {
                throw new APIManagementException(
                        "Key Manager Type " + keyManagerConfigurationDTO.getType() + " is invalid.",
                        ExceptionCodes.INVALID_KEY_MANAGER_TYPE);
            }
        }
    }

    /**
     * The method converts the date into timestamp
     *
     * @param workflowType workflow Type of workflow pending request
     * @param status       Workflow status of workflow pending request
     * @param tenantDomain tenant domain of user
     * @return Workflow[]  list of workflow pending requests
     * @throws APIManagementException
     */
    public Workflow[] getworkflows(String workflowType, String status, String tenantDomain)
            throws APIManagementException {
        return apiMgtDAO.getworkflows(workflowType, status, tenantDomain);
    }

    /**
     * The method converts the date into timestamp
     *
     * @param externelWorkflowRef External Workflow Reference of workflow pending request
     * @param status              Workflow status of workflow pending request
     * @param tenantDomain        tenant domain of user
     * @return Workflow           Workflow pending request
     * @throws APIManagementException
     */
    public Workflow getworkflowReferenceByExternalWorkflowReferenceID(String externelWorkflowRef, String status,
                                                                      String tenantDomain) throws APIManagementException {
        Workflow workflow = apiMgtDAO.getworkflowReferenceByExternalWorkflowReferenceID(externelWorkflowRef,
                status, tenantDomain);

        if (workflow == null) {
            String msg = "External workflow Reference: " + externelWorkflowRef + " was not found.";
            throw new APIMgtResourceNotFoundException(msg);
        }
        return workflow;
    }

    /**
     * This method used to check the existence of the scope name for the particular user
     *
     * @param username      username to be validated
     * @param scopeName     scope to be validated
     * @throws APIManagementException
     */
    public boolean isScopeExistsForUser(String username, String scopeName) throws APIManagementException {
        if (APIUtil.isUserExist(username)){
            Map<String, String> scopeRoleMapping =
                    APIUtil.getRESTAPIScopesForTenant(MultitenantUtils.getTenantDomain(username));
            if (scopeRoleMapping.containsKey(scopeName)) {
                String[] userRoles = APIUtil.getListOfRoles(username);
                return getRoleScopeList(userRoles,scopeRoleMapping).contains(scopeName);
            } else {
                throw new APIManagementException("Scope Not Found.  Scope : " + scopeName + ",",
                        ExceptionCodes.SCOPE_NOT_FOUND);
            }
        } else {
            throw new APIManagementException("User Not Found. Username :" + username + ",",
                    ExceptionCodes.USER_NOT_FOUND);
         }
    }

    /**
     * This method used to check the existence of the scope name
     * @param username      tenant username to get tenant-scope mapping
     * @param scopeName     scope to be validated
     * @throws APIManagementException
     */
    public boolean isScopeExists(String username, String scopeName)  {
        Map<String, String> scopeRoleMapping = APIUtil.getRESTAPIScopesForTenant(MultitenantUtils
                .getTenantDomain(username));
        return scopeRoleMapping.containsKey(scopeName);
    }

    /**
     * This method used to get the list of scopes of a user roles
     *
     * @param userRoles             roles of a particular user
     * @param scopeRoleMapping      scope-role mapping
     * @return scopeList            scope lost of a particular user
     * @throws APIManagementException
     */
    private List<String> getRoleScopeList(String[] userRoles, Map<String, String> scopeRoleMapping) {
        List<String> userRoleList;
        List<String> authorizedScopes = new ArrayList<>();

        if (userRoles == null || userRoles.length == 0) {
            userRoles = new String[0];
        }

        userRoleList = Arrays.asList(userRoles);
        Iterator<Map.Entry<String, String>> iterator = scopeRoleMapping.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            for (String aRole : entry.getValue().split(",")) {
                if (userRoleList.contains(aRole)) {
                    authorizedScopes.add(entry.getKey());
                }
            }
        }
        return authorizedScopes;
    }
}
