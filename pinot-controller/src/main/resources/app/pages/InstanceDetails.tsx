/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import React, { useState, useEffect } from 'react';
import { Button, FormControlLabel, Grid, makeStyles, Switch } from '@material-ui/core';
import { UnControlled as CodeMirror } from 'react-codemirror2';
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/material.css';
import 'codemirror/mode/javascript/javascript';
import { TableData } from 'Models';
import { RouteComponentProps } from 'react-router-dom';
import PinotMethodUtils from '../utils/PinotMethodUtils';
import AppLoader from '../components/AppLoader';
import CustomizedTables from '../components/Table';
import SimpleAccordion from '../components/SimpleAccordion';
import CustomButton from '../components/CustomButton';
import EditTagsOp from '../components/Homepage/Operations/EditTagsOp';
import EditConfigOp from '../components/Homepage/Operations/EditConfigOp';
import CustomNotification from '../components/CustomNotification';
import _ from 'lodash';
import Confirm from '../components/Confirm';

const useStyles = makeStyles((theme) => ({
  codeMirrorDiv: {
    border: '1px #BDCCD9 solid',
    borderRadius: 4,
    marginBottom: '20px',
  },
  codeMirror: {
    '& .CodeMirror': { maxHeight: 430, border: '1px solid #BDCCD9' },
  },
  operationDiv: {
    border: '1px #BDCCD9 solid',
    borderRadius: 4,
    marginBottom: 20,
  }
}));

const jsonoptions = {
  lineNumbers: true,
  mode: 'application/json',
  styleActiveLine: true,
  gutters: ['CodeMirror-lint-markers'],
  theme: 'default',
  readOnly: true
};

type Props = {
  instanceName: string
};

const InstanceDetails = ({ match }: RouteComponentProps<Props>) => {
  const classes = useStyles();
  const {instanceName} = match.params;
  const clutserName = localStorage.getItem('pinot_ui:clusterName');
  const [fetching, setFetching] = useState(true);
  const [instanceType] = React.useState(instanceName.toLowerCase().startsWith('broker') ? 'BROKER' : 'SERVER');
  const [confirmDialog, setConfirmDialog] = React.useState(false);
  const [dialogDetails, setDialogDetails] = React.useState(null);

  const [instanceConfig, setInstanceConfig] = useState(null);
  const [liveConfig, setLiveConfig] = useState(null);
  const [instanceDetails, setInstanceDetails] = useState(null);
  const [tableData, setTableData] = useState<TableData>({
    columns: [],
    records: []
  });
  const [tagsList, setTagsList] = useState([]);
  const [tagsErrorObj, setTagsErrorObj] = useState({isError: false, errorMessage: null})
  const [config, setConfig] = useState('{}');

  const [state, setState] = React.useState({
    enabled: true,
  });

  const [showEditTag, setShowEditTag] = useState(false);
  const [showEditConfig, setShowEditConfig] = useState(false);
  const [notificationData, setNotificationData] = React.useState({type: '', message: ''});
  const [showNotification, setShowNotification] = React.useState(false);

  const fetchData = async () => {
    const configResponse = await PinotMethodUtils.getInstanceConfig(clutserName, instanceName);
    const liveConfigResponse = await PinotMethodUtils.getLiveInstanceConfig(clutserName, instanceName);
    const instanceDetails = await PinotMethodUtils.getInstanceDetails(instanceName);
    const tenantListResponse = getTenants(instanceDetails);
    setInstanceConfig(JSON.stringify(configResponse, null, 2));
    const instanceHost = instanceDetails.hostName.replace(`${_.startCase(instanceType.toLowerCase())}_`, '');
    const instancePutObj = {
      host: instanceHost,
      port: instanceDetails.port,
      type: instanceType,
      tags: instanceDetails.tags
    };
    setState({enabled: instanceDetails.enabled});
    setInstanceDetails(JSON.stringify(instancePutObj, null, 2));
    setLiveConfig(JSON.stringify(liveConfigResponse, null, 2));
    if(tenantListResponse){
      fetchTableDetails(tenantListResponse);
    } else {
      setFetching(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const fetchTableDetails = (tenantList) => {
    const promiseArr = [];
    tenantList.map((tenantName) => {
      promiseArr.push(PinotMethodUtils.getTenantTableData(tenantName));
    });
    const tenantTableData = {
      columns: [],
      records: []
    };
    Promise.all(promiseArr).then((results)=>{
      results.map((result)=>{
        tenantTableData.columns = result.columns;
        tenantTableData.records.push(...result.records);
      });
      setTableData(tenantTableData);
      setFetching(false);
    });
  };

  const getTenants = (instanceDetails) => {
    const tenantsList = [];
    instanceDetails.tags.forEach((tag) => {
      if(tag.search('_BROKER') !== -1 ||
        tag.search('_REALTIME') !== -1 ||
        tag.search('_OFFLINE') !== -1
      ){
        tenantsList.push(tag.split('_')[0]);
      }
    });
    return _.uniq(tenantsList);
  };

  const handleTagsChange = (e: React.ChangeEvent<HTMLInputElement>, tags: Array<string>|null) => {
    isTagsValid(tags);
    setTagsList(tags);
  };

  const isTagsValid = (_tagsList) => {
    let isValid = true;
    setTagsErrorObj({isError: false, errorMessage: null});
    _tagsList.map((tag)=>{
      if(!isValid){
        return;
      }
      if(instanceType === 'BROKER'){
        if(!tag.endsWith('_BROKER')){
          isValid = false;
          setTagsErrorObj({
            isError: true,
            errorMessage: "Tags should end with _BROKER."
          });
        }
      } else if(instanceType === 'SERVER'){
        if(!tag.endsWith('_REALTIME') &&
          !tag.endsWith('_OFFLINE')
        ){
          isValid = false;
          setTagsErrorObj({
            isError: true,
            errorMessage: "Tags should end with _OFFLINE or _REALTIME.."
          });
        }
      }
    });
    return isValid;
  }

  const saveTagsAction = async () => {
    if(!isTagsValid(tagsList)){
      return;
    }
    const result = await PinotMethodUtils.updateTags(instanceName, tagsList);
    if(result.status){
      setNotificationData({type: 'success', message: result.status});
      fetchData();
    } else {
      setNotificationData({type: 'error', message: result.error});
    }
    setShowNotification(true);
    setShowEditTag(false);
  };

  const handleDropAction = () => {
    setDialogDetails({
      title: 'Drop Instance',
      content: 'Are you sure want to drop this instance?',
      successCb: () => dropInstance()
    });
    setConfirmDialog(true);
  };

  const dropInstance = async () => {
    const result = await PinotMethodUtils.deleteInstance(instanceName);
    if(result.status){
      setNotificationData({type: 'success', message: result.status});
      fetchData();
    } else {
      setNotificationData({type: 'error', message: result.error});
    }
    setShowNotification(true);
    closeDialog();
  };

  const handleSwitchChange = (event) => {
    setDialogDetails({
      title: state.enabled ? 'Disable Instance' : 'Enable Instance',
      content: `Are you sure want to ${state.enabled ? 'disable' : 'enable'} this instance?`,
      successCb: () => toggleInstanceState()
    });
    setConfirmDialog(true);
  };

  const toggleInstanceState = async () => {
    const result = await PinotMethodUtils.toggleInstanceState(instanceName, state.enabled ? 'DISABLE' : 'ENABLE');
    if(result.status){
      setNotificationData({type: 'success', message: result.status});
      fetchData();
    } else {
      setNotificationData({type: 'error', message: result.error});
    }
    setShowNotification(true);
    setState({ enabled: !state.enabled });
    closeDialog();
  };

  const handleConfigChange = (value: string) => {
    setConfig(value);
  };

  const saveConfigAction = async () => {
    if(JSON.parse(config)){
      const result = await PinotMethodUtils.updateInstanceDetails(instanceName, config);
      if(result.status){
        setNotificationData({type: 'success', message: result.status});
        fetchData();
      } else {
        setNotificationData({type: 'error', message: result.error});
      }
      setShowNotification(true);
      setShowEditConfig(false);
    }
  };

  const closeDialog = () => {
    setConfirmDialog(false);
    setDialogDetails(null);
  };

  return (
    fetching ? <AppLoader /> :
    <Grid
      item
      xs
      style={{
        padding: 20,
        backgroundColor: 'white',
        maxHeight: 'calc(100vh - 70px)',
        overflowY: 'auto',
      }}
    >
      {!instanceName.toLowerCase().startsWith('controller') &&
        <div className={classes.operationDiv}>
          <SimpleAccordion
            headerTitle="Operations"
            showSearchBox={false}
          >
            <div>
              <CustomButton
                onClick={()=>{
                  setTagsList(JSON.parse(instanceConfig)?.listFields?.TAG_LIST || []);
                  setShowEditTag(true);
                }}
              >
                Edit Tags
              </CustomButton>
              <CustomButton
                onClick={()=>{
                  setConfig(instanceDetails);
                  setShowEditConfig(true);
                }}
              >
                Edit Config
              </CustomButton>
              <CustomButton onClick={handleDropAction}>
                Drop
              </CustomButton>
              <FormControlLabel
                control={
                  <Switch
                    checked={state.enabled}
                    onChange={handleSwitchChange}
                    name="enabled"
                    color="primary"
                  />
                }
                label="Enable"
              />
            </div>
          </SimpleAccordion>
        </div>}
      <Grid container spacing={2}>
        <Grid item xs={liveConfig ? 6 : 12}>
          <div className={classes.codeMirrorDiv}>
            <SimpleAccordion
              headerTitle="Instance Config"
              showSearchBox={false}
            >
              <CodeMirror
                options={jsonoptions}
                value={instanceConfig}
                className={classes.codeMirror}
                autoCursor={false}
              />
            </SimpleAccordion>
          </div>
        </Grid>
        {liveConfig ?
          <Grid item xs={6}>
            <div className={classes.codeMirrorDiv}>
              <SimpleAccordion
                headerTitle="LiveInstance Config"
                showSearchBox={false}
              >
                <CodeMirror
                  options={jsonoptions}
                  value={liveConfig}
                  className={classes.codeMirror}
                  autoCursor={false}
                />
              </SimpleAccordion>
            </div>
          </Grid>
          : null}
      </Grid>
      {tableData.columns.length ?
        <CustomizedTables
          title="Tables"
          data={tableData}
          isPagination
          addLinks
          baseURL={`/instance/${instanceName}/table/`}
          showSearchBox={true}
          inAccordionFormat={true}
        />
        : null}
      <EditTagsOp
        showModal={showEditTag}
        hideModal={()=>{setShowEditTag(false);}}
        saveTags={saveTagsAction}
        tags={tagsList}
        handleTagsChange={handleTagsChange}
        error={tagsErrorObj}
      />
      <EditConfigOp
        showModal={showEditConfig}
        hideModal={()=>{setShowEditConfig(false);}}
        saveConfig={saveConfigAction}
        config={config}
        handleConfigChange={handleConfigChange}
      />
      {confirmDialog && dialogDetails && <Confirm
        openDialog={confirmDialog}
        dialogTitle={dialogDetails.title}
        dialogContent={dialogDetails.content}
        successCallback={dialogDetails.successCb}
        closeDialog={closeDialog}
        dialogYesLabel='Yes'
        dialogNoLabel='No'
      />}
      <CustomNotification
        type={notificationData.type}
        message={notificationData.message}
        show={showNotification}
        hide={()=>{setShowNotification(false)}}
      />
    </Grid>
  );
};

export default InstanceDetails;