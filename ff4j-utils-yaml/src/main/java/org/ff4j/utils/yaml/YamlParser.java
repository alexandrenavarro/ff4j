package org.ff4j.utils.yaml;

/*-
 * #%L
 * ff4j-utils-yaml
 * %%
 * Copyright (C) 2013 - 2018 FF4J
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ff4j.feature.Feature;
import org.ff4j.feature.strategy.TogglePredicate;
import org.ff4j.parser.AbstractConfigurationFileParser;
import org.ff4j.parser.FF4jConfigFile;
import org.ff4j.property.Property;
import org.ff4j.property.domain.PropertyBoolean;
import org.ff4j.property.domain.PropertyDouble;
import org.ff4j.property.domain.PropertyInt;
import org.ff4j.property.domain.PropertyString;
import org.ff4j.security.domain.FF4jAcl;
import org.ff4j.security.domain.FF4jGrantees;
import org.ff4j.security.domain.FF4jPermission;
import org.ff4j.security.domain.FF4jUser;
import org.ff4j.test.AssertUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Parser to read {@link FF4jConfigFile} from a YAML file.
 *
 * @author Cedrick LUNVEN (@clunven)
 */
public class YamlParser extends AbstractConfigurationFileParser {
    
    /**
     * Default constructor.
     */
    public YamlParser() {}
    
    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public FF4jConfigFile parse(InputStream inputStream) {
        AssertUtils.assertNotNull(inputStream, "Cannot read file stream is empty, check readability and path.");
        Yaml yaml = new Yaml();
        Map<?,?> yamlConfigFile = yaml.load(inputStream);
        Map<?,?> ff4jYamlMap = (Map<?, ?>) yamlConfigFile.get(FF4J_TAG);
        FF4jConfigFile ff4jConfig = new FF4jConfigFile();
        // Audit
        if (ff4jYamlMap.containsKey(GLOBAL_AUDIT_TAG)) {
            ff4jConfig.setAudit(Boolean.valueOf(ff4jYamlMap.containsKey(GLOBAL_AUDIT_TAG)));
        }
        // AutoCreate
        if (ff4jYamlMap.containsKey(GLOBAL_AUTOCREATE)) {
            ff4jConfig.setAutoCreate(Boolean.valueOf(ff4jYamlMap.containsKey(GLOBAL_AUTOCREATE)));
        }
        if (ff4jYamlMap != null) {
            // Roles
            parseRoles(ff4jConfig, (List<Map<String, Object>>) ff4jYamlMap.get(SECURITY_ROLES_TAG));
            // Users
            parseUsers(ff4jConfig, (List<Map<String, Object>>) ff4jYamlMap.get(USERS_TAG));
            // ACLS
            parseAcls(ff4jConfig, (List<Map<String, Object>>) ff4jYamlMap.get(GLOBAL_PERMISSIONS_TAG));
            // Properties
            ff4jConfig.getProperties().putAll(
                       parseProperties((List<Map<String, Object>>) ff4jYamlMap.get(PROPERTIES_TAG))
            );
            // Features
            parseFeatures(ff4jConfig, (List<Map<String, Object>>) ff4jYamlMap.get(FEATURES_TAG));
        }
        return ff4jConfig;
    }
    
    /**
     * Read roles map and populate configuration bean.
     * 
     * @param ff4jConfig
     *      current configuration
     * @param rolesMap
     *      role map.
     */
    @SuppressWarnings("unchecked")
    private void parseRoles(FF4jConfigFile ff4jConfig, List<Map<String, Object>> roleItems) {
        if (null != roleItems) {
            roleItems.forEach(role -> {
                ff4jConfig.getRoles().put(
                        (String) role.get(SECURITY_ROLE_ATTNAME), 
                        new HashSet<String>((Collection<String>) role.get(SECURITY_PERMISSIONS_TAG)));
            }); 
        }
    }
    
    /**
     * Use 'users' tag to define users and their passwords.
     *
     * @param ff4jConfig
     *      configuration to populate
     * @param usersItems
     *      items loaded fron Yaml file.
     */
    @SuppressWarnings("unchecked")
    private void parseUsers(FF4jConfigFile ff4jConfig, List<Map<String, Object>> usersItems) {
        if (null != usersItems) {
            usersItems.forEach(user -> {
                FF4jUser currentUser = new FF4jUser( (String) user.get(USER_ATT_UID));
                currentUser.setDescription((String) user.get(USER_ATT_DESC));
                currentUser.setLastName((String) user.get(USER_ATT_LASTNAME));
                currentUser.setFirstName((String) user.get(USER_ATT_FIRSTNAME));
                if (user.containsKey(USER_ATT_ROLES)) {
                    currentUser.setRoles(new HashSet<String>((Collection<String>) user.get(USER_ATT_ROLES)));
                }
                if (user.containsKey(USER_ATT_PERMISSIONS)) {
                    currentUser.setPermissions(
                            ((Collection<String>) user.get(USER_ATT_PERMISSIONS))
                                .stream()
                                .map(FF4jPermission::valueOf)
                                .collect(Collectors.toSet()));
                }
                ff4jConfig.getUsers().put(currentUser.getUid(), currentUser);
            }); 
        }
    }

    private void parseAcls(FF4jConfigFile ff4jConfig, List<Map<String, Object>> permItems) {
        if (null != permItems) {
            permItems.forEach(perm -> {
                // Key
                String target = (String) perm.get(GLOBAL_PERMISSIONS_TARGET);
                if (!ff4jConfig.getAcls().containsKey(target)) {
                    ff4jConfig.getAcls().put(target, new FF4jAcl());
                }
                ff4jConfig.getAcls().get(target)
                          .getPermissions()
                          .putAll(parsePermission(perm));
            });
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<FF4jPermission, FF4jGrantees> parsePermission(Map<String, Object> yamlPermission) {
        List < String > users = (List<String>) yamlPermission.get(GLOBAL_PERMISSIONS_USERS);
        List < String > roles = (List<String>) yamlPermission.get(GLOBAL_PERMISSIONS_ROLES);
        Map<FF4jPermission, FF4jGrantees> value = new HashMap<>();
        value.put(
                FF4jPermission.valueOf((String) yamlPermission.get(GLOBAL_PERMISSIONS_NAME)), 
                new FF4jGrantees(users == null ? new HashSet<>() : new HashSet<>(users), 
                                 roles == null ? new HashSet<>() : new HashSet<>(roles)));
        return value;
    }
   
    @SuppressWarnings("unchecked")
    private Map < String, Property<?>> parseProperties(List<Map<String, Object>> properties) {
        Map < String, Property<?>> result = new HashMap<>();
        if (null != properties) {
            properties.forEach(property -> {
                // Initiate with name and value
                String name = (String) property.get(PROPERTY_PARAMNAME);
                if (null == name) throw new IllegalArgumentException("Invalid YAML File: 'name' is expected for properties"); 
                Object value = property.get(PROPERTY_PARAMVALUE);
                if (null == value) throw new IllegalArgumentException("Invalid YAML File: 'value' is expected for properties");
               
                // Enforcing type
                Property<?> ap = null;
                String type = (String) property.get(PROPERTY_PARAMTYPE);
                if (null != type) {
                    type = Property.mapPropertyType(type);
                    try {
                        ap = (Property<?>) Class.forName(type)
                                .getConstructor(String.class, String.class)
                                .newInstance(name, String.valueOf(value));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Cannot instantiate '" + type + "' check default constructor", e);
                    }
                } else if (value instanceof Integer) {
                    ap = new PropertyInt(name, (Integer) value);
                } else if (value instanceof Double)  {
                    ap = new PropertyDouble(name, (Double) value);
                } else if (value instanceof Boolean) {
                    ap = new PropertyBoolean(name, (Boolean) value);
                } else {
                    ap = new PropertyString(name, value.toString());
                }
                
                // Description
                String description = (String) property.get(PROPERTY_PARAMDESCRIPTION);
                if (null == description) ap.setDescription(description);
                
                // ReadOnly
                Boolean readOnly = (Boolean) property.get(PROPERTY_PARAM_READONLY);
                if (null != readOnly) ap.setReadOnly(readOnly);
                
                // Fixed Values
                List<Object> fixedValues = (List<Object>) property.get(PROPERTY_PARAMFIXED_VALUES);
                if (null != fixedValues && fixedValues.size() > 0) {
                    fixedValues.stream().map(Object::toString).forEach(ap::add2FixedValueFromString);
                }
                result.put(ap.getUid(), ap);
            });
        }
        return result;
    }
    
    
    /**
     * Parsing YAML TAGS.
     *
     * @param ff4jConfig
     *      current configuration
     * @param features
     *      feature to populate
     */
    @SuppressWarnings("unchecked")
    private void parseFeatures(FF4jConfigFile ff4jConfig, List<Map<String, Object>> features) {
        if (null != features) {
            features.forEach(feature -> {
                String name = (String) feature.get(FEATURE_ATT_UID);
                if (null == name) throw new IllegalArgumentException("Invalid YAML File: 'uid' is expected for feature");
                Feature f = new Feature(name);
                // Enabled
                Boolean enabled = (Boolean) feature.get(FEATURE_ATT_ENABLE);
                if (null != enabled) f.setEnable(enabled);
                // Description
                String description = (String) feature.get(FEATURE_ATT_DESC);
                if (null != description) f.setDescription(description);
                // Group
                String groupName = (String) feature.get(FEATURE_ATT_GROUP);
                if (null != groupName) f.setGroup(groupName);
                
                // Toggle Strategies
                List<Map<String, Object>> toggleStrategies = 
                        (List<Map<String, Object>>) feature.get(TOGGLE_STRATEGIES_TAG);
                if (toggleStrategies != null) {
                    toggleStrategies.stream()
                                    .map(map -> parseToggleStrategy(f,map)).forEach(f::addToggleStrategy);
                }
                
                // Security
                List<Map<String, Object>> customPermissons = 
                        (List<Map<String, Object>>) feature.get(SECURITY_PERMISSIONS_TAG);
                if (customPermissons != null) {
                    FF4jAcl customAcl = new FF4jAcl();
                    customPermissons.stream().map(this::parsePermission)
                                    .forEach(map -> customAcl.getPermissions().putAll(map));
                    f.setAccessControlList(customAcl);
                }
                
                // Custom Properties
                List<Map<String, Object>> customProperties = 
                        (List<Map<String, Object>>) feature.get(PROPERTIES_CUSTOM_TAG);
                if (customProperties != null) {
                    f.setCustomProperties(parseProperties(customProperties));
                }
                
                ff4jConfig.getFeatures().put(f.getUid(), f);
            });
        }
    }
    
    @SuppressWarnings("unchecked")
    private TogglePredicate parseToggleStrategy(Feature feature, Map<String, Object> toggleStrategy) {
        Map <String, String > initParams = new HashMap<>();
        Map<String, Object> p = (Map<String, Object>) toggleStrategy.get(TOGGLE_STRATEGY_PARAMTAG);
        if (p != null) {
            p.entrySet().forEach(entry -> initParams.put(
                     entry.getKey(), 
                     String.valueOf(entry.getValue())));
        }
        return TogglePredicate.of(
                feature.getUid(), 
                (String) toggleStrategy.get(TOGGLE_STRATEGY_ATTCLASS), 
                initParams);
    }
    
    /** {@inheritDoc} */
    @Override
    public String export(FF4jConfigFile config) {
        // TODO Auto-generated method stub
        return null;
    }   
    
    
}