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

import React, { useEffect, useState } from 'react';
import { createStyles, DialogContent, Grid, makeStyles, Theme} from '@material-ui/core';
import Dialog from '../../CustomDialog';
import SimpleAccordion from '../../SimpleAccordion';
import AddTableComponent from './AddTableComponent';
import CustomCodemirror from '../../CustomCodemirror';
import PinotMethodUtils from '../../../utils/PinotMethodUtils';
import { NotificationContext } from '../../Notification/NotificationContext';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    sqlDiv: {
      border: '1px #BDCCD9 solid',
      borderRadius: 4,
      marginBottom: '20px',
    },
    queryOutput: {
      '& .CodeMirror': { height: '532px !important' },
    },
  })
);

type Props = {
  hideModal: (event: React.MouseEvent<HTMLElement, MouseEvent>) => void,
  fetchData: Function
};

const defaultTableObj = {
  "tableName": "",
  "tableType": "",
  "tenants": {
    "broker": "DefaultTenant",
    "server": "DefaultTenant",
    "tagOverrideConfig": {}
  },
  "segmentsConfig": {
    "schemaName": "",
    "timeColumnName": null,
    "replication": "1",
    "replicasPerPartition": "1",
    "retentionTimeUnit": null,
    "retentionTimeValue": null,
    "segmentPushType": "APPEND",
    "segmentPushFrequency": "HOURLY",
    "completionConfig": null,
    "crypterClassName": null,
    "peerSegmentDownloadScheme": null
  },
  "tableIndexConfig": {
    "loadMode": "MMAP",
    "invertedIndexColumns": [],
    "createInvertedIndexDuringSegmentGeneration": false,
    "rangeIndexColumns": [],
    "sortedColumn": [],
    "bloomFilterColumns": [],
    "bloomFilterConfigs": null,
    "noDictionaryColumns": [],
    "onHeapDictionaryColumns": [],
    "varLengthDictionaryColumns": [],
    "enableDefaultStarTree": false,
    "starTreeIndexConfigs": null,
    "enableDynamicStarTreeCreation": false,
    "segmentPartitionConfig": null,
    "columnMinMaxValueGeneratorMode": null,
    "aggregateMetrics": false,
    "nullHandlingEnabled": false,
    "streamConfigs": {
      "streamType": "kafka",
      "stream.kafka.topic.name": "",
      "stream.kafka.broker.list": "",
      "stream.kafka.consumer.type": "lowlevel",
      "stream.kafka.consumer.prop.auto.offset.reset": "smallest",
      "stream.kafka.consumer.factory.class.name": "org.apache.pinot.plugin.stream.kafka20.KafkaConsumerFactory",
      "stream.kafka.decoder.class.name": "org.apache.pinot.plugin.stream.kafka.KafkaJSONMessageDecoder",
      "stream.kafka.decoder.prop.schema.registry.rest.url": null,
      "stream.kafka.decoder.prop.schema.registry.schema.name": null,
      "realtime.segment.flush.threshold.rows": "0",
      "realtime.segment.flush.threshold.time": "24h",
      "realtime.segment.flush.segment.size": "100M"
    }
  },
  "metadata": {},
  "ingestionConfig": {
    "filterConfig": null,
    "transformConfigs": null
  },
  "quota": {
    "storage": null,
    "maxQueriesPerSecond": null
  },
  "task": null,
  "routing": {
    "segmentPrunerTypes": null,
    "instanceSelectorType": null
  },
  "instanceAssignmentConfigMap": null,
  "query": {
    "timeoutMs": null
  },
  "fieldConfigList": null,
  "upsertConfig": null,
  "tierConfigs": null
};

const defaultSchemaObj = {
  schemaName: '',
  dimensionFieldSpecs: [],
  metricFieldSpecs: [],
  dateTimeFieldSpecs: []
};

let timerId = null;

export default function AddTableOp({
  hideModal,
  fetchData
}: Props) {
  const classes = useStyles();
  const [tableObj, setTableObj] = useState({...defaultTableObj});
  const [schemaObj, setSchemaObj] = useState({...defaultSchemaObj});
  const [tableName, setTableName] = useState('');
  const {dispatch} = React.useContext(NotificationContext);

  useEffect(()=>{
    if(tableName !== tableObj.tableName){
      setTableName(tableObj.tableName);
      clearTimeout(timerId);
      timerId = setTimeout(()=>{
        updateSchemaObj(tableObj.tableName);
      }, 1000);
    }
  }, [tableObj]);

  const updateSchemaObj = async (tableName) => {
    //table name is same as schema name
    const schemaObj = await PinotMethodUtils.getSchemaData(tableName);
    if(schemaObj.error){
      dispatch({
        type: 'error',
        message: schemaObj.error,
        show: true
      });
      setSchemaObj(defaultSchemaObj)
    } else {
      setSchemaObj({...defaultSchemaObj, ...schemaObj});
    }
  }
  
  const validateTableConfig = async () => {
    const validTable = await PinotMethodUtils.validateTableAction(tableObj);
    if(validTable.error){
      dispatch({
        type: 'error',
        message: validTable.error,
        show: true
      });
      return false;
    }
    return true;
  };

  const handleSave = async () => {
    if(await validateTableConfig()){
      const tableCreationResp = await PinotMethodUtils.saveTableAction(tableObj);
      dispatch({
        type: tableCreationResp.error ? 'error' : 'success',
        message: tableCreationResp.error || tableCreationResp.status,
        show: true
      });
      tableCreationResp.status && fetchData();
      tableCreationResp.status && hideModal(null);
    }
  };

  return (
    <Dialog
      open={true}
      handleClose={hideModal}
      handleSave={handleSave}
      title="Add Table"
      size="xl"
      disableBackdropClick={true}
      disableEscapeKeyDown={true}
    >
      <DialogContent>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <SimpleAccordion
              headerTitle="Add Table"
              showSearchBox={false}
            >
              <AddTableComponent
                tableObj={tableObj}
                setTableObj={setTableObj}
                dateTimeFieldSpecs={schemaObj.dateTimeFieldSpecs}
              />
            </SimpleAccordion>
          </Grid>
          <Grid item xs={6}>
            <div className={classes.sqlDiv}>
              <SimpleAccordion
                headerTitle="Table Config"
                showSearchBox={false}
              >
                <CustomCodemirror
                  customClass={classes.queryOutput}
                  data={tableObj}
                  isEditable={true}
                  returnCodemirrorValue={(newValue)=>{
                    try{
                      const jsonObj = JSON.parse(newValue);
                      if(jsonObj){
                        jsonObj.segmentsConfig.replicasPerPartition = jsonObj.segmentsConfig.replication;
                        jsonObj.segmentsConfig.schemaName = jsonObj.tableName;
                        setTableObj(jsonObj);
                      }
                    }catch(e){}
                  }}
                />
              </SimpleAccordion>
            </div>
          </Grid>
          <Grid item xs={6}>
            <div className={classes.sqlDiv}>
              <SimpleAccordion
                headerTitle="Schema Config (Read only)"
                showSearchBox={false}
              >
                <CustomCodemirror
                  customClass={classes.queryOutput}
                  data={schemaObj}
                  isEditable={false}
                />
              </SimpleAccordion>
            </div>
          </Grid>
        </Grid>
      </DialogContent>
    </Dialog>
  );
}