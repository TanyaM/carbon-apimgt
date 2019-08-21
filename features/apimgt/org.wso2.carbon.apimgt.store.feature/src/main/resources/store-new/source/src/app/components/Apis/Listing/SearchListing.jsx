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
import qs from 'qs';
import Grid from '@material-ui/core/Grid';
import Typography from '@material-ui/core/Typography';
import IconButton from '@material-ui/core/IconButton';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import List from '@material-ui/icons/List';
import { FormattedMessage } from 'react-intl';
import GridIcon from '@material-ui/icons/GridOn';
import queryString from 'query-string';
import ApiThumb from 'AppComponents/Apis/Listing/ApiThumb';
import DocThumb from 'AppComponents/Apis/Listing/DocThumb';
import ResourceNotFound from '../../Base/Errors/ResourceNotFound';
import Loading from '../../Base/Loading/Loading';
import API from '../../../data/api';
import CustomIcon from '../../Shared/CustomIcon';
import ApiTableView from './ApiTableView';


/**
 *
 *
 * @param {*} theme
 */
const styles = theme => ({
    rightIcon: {
        marginLeft: theme.spacing.unit,
    },
    button: {
        margin: theme.spacing.unit,
        marginBottom: 0,
    },
    buttonRight: {
        alignSelf: 'flex-end',
        display: 'flex',
    },
    ListingWrapper: {
        paddingTop: 10,
        paddingLeft: 35,
        width: theme.custom.contentAreaWidth,
    },
    root: {
        height: 70,
        background: theme.palette.background.paper,
        borderBottom: 'solid 1px ' + theme.palette.grey.A200,
        display: 'flex',
    },
    mainIconWrapper: {
        paddingTop: 13,
        paddingLeft: 35,
        paddingRight: 20,
    },
    mainTitle: {
        paddingTop: 10,
    },
    mainTitleWrapper: {
        flexGrow: 1,
    },
    content: {
        flexGrow: 1,
    },
});
/**
 *
 *
 * @class Listing
 * @extends {React.Component}
 */
class SearchListing extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            searchResults: null,
        };
        this.state.listType = this.props.theme.custom.defaultApiView;
    }

    /**
     *
     *
     * @memberof SearchListing
     */
    componentDidMount() {
        this.getSearchResults();
    }

    /**
     * Update the component based on the provided search query
     * @param prevProps
     */
    componentDidUpdate(prevProps) {
        const { location: { search } } = this.props;
        if (prevProps.location.search !== search) {
            this.getSearchResults();
        }
    }

    /**
     *
     *
     * @memberof SearchListing
     */
    setListType = (value) => {
        this.setState({ listType: value });
    };

    getSearchResults = () => {
        const { history, location: { search, pathname } } = this.props;
        const api = new API();
        const promisedSearchResults = api.search(queryString.parse(search));
        promisedSearchResults
            .then((response) => {
                this.setState({ searchResults: response.obj });
            })
            .catch((error) => {
                const { status } = error;
                if (status === 404) {
                    this.setState({ notFound: true });
                } else if (status === 401) {
                    const params = qs.stringify({ reference: pathname });
                    history.push({ pathname: '/login', search: params });
                }
            });
    };

    /**
     * render searchResults in search result listing
     */
    renderSearchResults = () => {
        const { classes, searchResults, listType } = this.state;
        if (listType === 'grid') {
            <Grid container className={classes.ListingWrapper}>
                {searchResults.list.map(searchResult => (
                    searchResult.type === 'API'
                        ? <ApiThumb api={searchResult} key={searchResult.id} />
                        : <DocThumb doc={searchResult} key={searchResult.id} />
                ))}
            </Grid>
        } else {
            <React.Fragment>
                <ApiTableView searchResults={searchResults.list}/>
            </React.Fragment>
        }
    }

    /**
     *
     *
     * @returns
     * @memberof SearchListing
     */
    render() {
        const { notFound, searchResults, listType } = this.state;
        if (notFound) {
            return <ResourceNotFound />;
        }
        const { theme, classes } = this.props;
        const strokeColorMain = theme.palette.getContrastText(theme.palette.background.paper);

        return (
            <main className={classes.content}>
                <div className={classes.root}>
                    <div className={classes.mainIconWrapper}>
                        <CustomIcon strokeColor={strokeColorMain} width={42} height={42} icon='api' />
                    </div>
                    <div className={classes.mainTitleWrapper}>
                        <Typography variant='display1' className={classes.mainTitle}>
                            <FormattedMessage defaultMessage='APIs & Docs' id='Apis.Listing.SearchListing.apis.main' />
                        </Typography>
                    </div>
                    <div className={classes.buttonRight}>
                        <IconButton className={classes.button} onClick={() => this.setListType('list')}>
                            <List color={listType === 'list' ? 'primary' : 'default'} />
                        </IconButton>
                        <IconButton className={classes.button} onClick={() => this.setListType('grid')}>
                            <GridIcon color={listType === 'grid' ? 'primary' : 'default'} />
                        </IconButton>
                    </div>
                </div>

                <Grid container spacing={0} justify='center'>
                    <Grid item xs={12}>
                        {searchResults ? (
                            listType === 'grid' ? (
                                <Grid container className={classes.ListingWrapper}>
                                    {searchResults.list.map(searchResult => (
                                        searchResult.type === 'API'
                                            ? <ApiThumb api={searchResult} key={searchResult.id} />
                                            : <DocThumb doc={searchResult} key={searchResult.id} />
                                    ))}
                                </Grid>
                            ) : (
                                <React.Fragment>
                                    <ApiTableView searchResults={searchResults.list} />
                                </React.Fragment>
                            )
                        ) : (
                            <Loading />
                        )}
                    </Grid>
                </Grid>
            </main>
        );
    }
}

SearchListing.propTypes = {
    classes: PropTypes.object.isRequired,
    theme: PropTypes.object.isRequired,
    location: PropTypes.string.isRequired,
    history: PropTypes.shape({
        push: PropTypes.func,
    }).isRequired,
};

export default withStyles(styles, { withTheme: true })(SearchListing);
