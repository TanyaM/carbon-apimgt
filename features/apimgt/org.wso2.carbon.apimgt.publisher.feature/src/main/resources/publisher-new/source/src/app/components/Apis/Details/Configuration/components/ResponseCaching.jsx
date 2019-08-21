/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import Grid from '@material-ui/core/Grid';
import Switch from '@material-ui/core/Switch';
import FormControl from '@material-ui/core/FormControl';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import FormLabel from '@material-ui/core/FormLabel';
import { FormattedMessage } from 'react-intl';
import Tooltip from '@material-ui/core/Tooltip';
import HelpOutline from '@material-ui/icons/HelpOutline';

/**
 *
 *
 * @export
 * @param {*} props
 * @returns
 */
export default function ResponseCaching(props) {
    const { api, configDispatcher } = props;
    return (
        <Grid container spacing={1} alignItems='flex-start'>
            <Grid item>
                <FormControl component='fieldset'>
                    <FormLabel component='legend'>
                        <FormattedMessage
                            id='Apis.Details.Configuration.Configuration.response.caching'
                            defaultMessage='Response caching'
                        />
                    </FormLabel>
                    <FormControlLabel
                        control={
                            <Switch
                                checked={api.responseCaching}
                                onChange={({ target: { checked } }) =>
                                    configDispatcher({
                                        action: 'responseCaching',
                                        value: checked,
                                    })
                                }
                                color='primary'
                            />
                        }
                    />
                </FormControl>
            </Grid>
            <Grid item>
                <Tooltip
                    title={
                        <FormattedMessage
                            id='Apis.Details.Configuration.components.ResponseCaching.tooltip'
                            defaultMessage={
                                'If enabled, API response will be cached at the gateway level' +
                                ' to improve the response time and minimize the backend load'
                            }
                        />
                    }
                    aria-label='Response cache'
                    placement='right-end'
                    interactive
                >
                    <HelpOutline />
                </Tooltip>
            </Grid>
        </Grid>
    );
}

ResponseCaching.propTypes = {
    api: PropTypes.shape({}).isRequired,
    configDispatcher: PropTypes.func.isRequired,
};
