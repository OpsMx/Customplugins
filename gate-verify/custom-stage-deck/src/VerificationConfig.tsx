import React, { useEffect, useState } from 'react';
import Modal from 'react-modal';
import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikFormField,
  FormikStageConfig,
  FormValidator,
  HelpContentsRegistry,
  HelpField,
  IExecutionDetailsSectionProps,
  IStage,
  TextInput,
  RadioButtonInput,
  DayPickerInput,
  IStageConfigProps,
  IStageTypeConfig,
  NumberInput,
  Validators,
  ReactSelectInput,
  useData,
  IFormikStageConfigInjectedProps,
  LayoutProvider,
  StandardFieldLayout,
  IStageForSpelPreview,
  Tooltip,
  SETTINGS
} from '@spinnaker/core';
import './Verification.less';
import { DateTimePicker } from './input/DateTimePickerInput';
import { REST } from '@spinnaker/core';
import { EvaluateVariablesStageForm } from './input/dynamicFormFields';
import opsMxLogo from './images/OpsMx_logo_Black.svg';
import openInNewTab from './images/open-new-tab-bold.png';

/*
  IStageConfigProps defines properties passed to all Spinnaker Stages.
  See IStageConfigProps.ts (https://github.com/spinnaker/deck/blob/master/app/scripts/modules/core/src/pipeline/config/stages/common/IStageConfigProps.ts) for a complete list of properties.
  Pass a JSON object to the `updateStageField` method to add the `maxWaitTime` to the Stage.

  This method returns JSX (https://reactjs.org/docs/introducing-jsx.html) that gets displayed in the Spinnaker UI.
 */

const HorizontalRule = () => (
  <div className="grid-span-4">
    <hr />
  </div>
);

export function VerificationConfig(props: IStageConfigProps) {

  console.log(props);
 

  const [applicationId, setApplicationId] = useState()

  const [metricDropdownList, setMetricDropdownList] = useState([])

  const [logDropdownList, setLogDropdownList] = useState([])

  const [environmentsList, setenvironmentsList] = useState([]);

  const [showEnvironment, setshowEnvironment] = useState(false);

  //const [newEnvironment, setnewEnvironment] = useState("");

  const [metricCreateUrl, setMetricCreateUrl] = useState('')

  const [metricUrl, setMetricUrl] = useState('');

  const [modalIsOpen, setModalIsOpen] = useState(false);

  const [metricListUpdated, onmetricListUpdated] = useState(false);

  const [logCreateUrl, setLogCreateUrl] = useState('');

  const [logmodalIsOpen, setLogModalIsOpen] = useState(false);

  const [logListUpdated, onlogListUpdated] = useState(false);

  const [logUrl, setLogUrl] = useState('');

  const [deleteMetricModalIsOpen, setDeleteMetricModalIsOpen] = useState(false);

  const [deleteLogModalIsOpen, setDeleteLogModalIsOpen] = useState(false);

  const [logTemplate, setLogTemplate] = useState();

  const [metricTemplate, setMetricTemplate] = useState();

  const [canaryRealTime,setcanaryRealTime] = useState(false);

  const [baselineRealTime,setbaselineRealTime] = useState(false);

  const[customenv,setCustomEnv]=useState('');

  const[isdUrl,setIsdUrl]=useState('');

  const [modeType, setModeType] = useState(null);
  
  const handleInput=(event: any)=>{
      event.preventDefault();
      setCustomEnv(event.target.value.toLowerCase()); 
      props.stage.parameters.customEnvironment = event.target.value.toLowerCase();     
  }

  const getGateSecurityParams = () => {
    if (!props.stage.parameters.hasOwnProperty('gateSecurity')) {
      props.stage.parameters.gateSecurity = [
        {
          "connectorType": "PayloadConstraints",
          "helpText": "Payload Constraints for Gate Security",
          "isMultiSupported": true,
          "label": "Payload Constraints",
          "selectInput": false,
          "supportedParams": [
            {
              "helpText": "Key",
              "label": "Key",
              "name": "label",
              "type": "string"
            },
            {
              "helpText": "Value",
              "label": "Value",
              "name": "value",
              "type": "string"
            }
          ],
          "values": [
            // {
            //   "label": "",
            //   "value": ""
            // }
          ]
        }
      ]
    }
  }


  useEffect(() => {
    let tempUrl = '';
    if(window && window.uiUrl){
      tempUrl = window.uiUrl;
    }
    else if(SETTINGS && SETTINGS.gateUrl && (SETTINGS.gateUrl !="/gate/" && SETTINGS.gateUrl !="/gate")){
      let gateurl = SETTINGS.gateUrl;
      if(gateurl.endsWith('/gate') || gateurl.endsWith('/gate/')){
       gateurl = gateurl.replace('/gate','');
      }
      tempUrl = gateurl;
    }
    else{
      tempUrl = window.location.origin;
    }
    setIsdUrl(tempUrl)
    if (applicationId != undefined) {
      REST('autopilot/api/v1/applications/' + applicationId + '/metricTemplates').
        get()
        .then(
          function (results) {
            const response = results['metricTemplates'];
            if(modeType == 'add'){
              const selectItem = response.filter(({ templateName : id1 }) => !metricDropdownList.some(({ templateName: id2 }) => id2 === id1));
              if (selectItem && selectItem?.length > 0) {
                props.stage.parameters.metricTemplate = selectItem[0].templateName;
                setMetricTemplate(selectItem[0].templateName);
                setModeType(null);
              }
            }
            setMetricDropdownList(response);
          }
        );
    } else {
      REST('platformservice/v2/applications/name/' + props.application['applicationName']).
        get()
        .then(
          (results) => {
            setApplicationId(results.applicationId);
            REST('autopilot/api/v1/applications/' + results.applicationId + '/metricTemplates').
              get()
              .then(
                function (results) {
                  const response = results['metricTemplates'];
                  if(modeType == 'add'){
                    const selectItem = response.filter(({ templateName : id1 }) => !metricDropdownList.some(({ templateName: id2 }) => id2 === id1));
                    if (selectItem && selectItem?.length > 0) {
                      props.stage.parameters.metricTemplate = selectItem[0].templateName;
                      setMetricTemplate(selectItem[0].templateName);
                      setModeType(null);
                    }
                }
                  setMetricDropdownList(response);
                }
              );
            REST('platformservice/v4/applications/' + results.applicationId).
              get()
              .then(
                function (results) {
                  if (results['services'].length > 0) {
                    let index = results['services'].map((i: { serviceName: any; }) => i.serviceName).indexOf(props.pipeline.name);
                    props.stage['serviceId'] = results['services'][index].serviceId;
                    //props.stage['pipelineId'] = results['services'][index].serviceId;
                    const pipelines = results.services[index].pipelines;
                    const pipelineIndex = pipelines.findIndex((pipeline: any) => pipeline.pipelineName == props.pipeline.name);
                    if (pipelineIndex >= 0) {
                      props.stage['pipelineId'] = pipelines[pipelineIndex].pipelineId;
                    }
                  }
                }
              );
            props.stage['applicationId'] = results.applicationId;            
            let a = tempUrl + "/ui/plugin-isd/metric-template/" + props.application['applicationName'] + "/" + results.applicationId + "/null/{}/" + props.application.attributes.email + "/-1/false/false/fromPlugin";
            setMetricCreateUrl(a);
          }
        )
    }
  }, [metricListUpdated])

  useEffect(() => {
    let tempUrl = '';
    if(window && window.uiUrl){
      tempUrl = window.uiUrl;
    }
    else if(SETTINGS && SETTINGS.gateUrl && (SETTINGS.gateUrl !="/gate/" && SETTINGS.gateUrl !="/gate")){
      let gateurl = SETTINGS.gateUrl;
      if(gateurl.endsWith('/gate') || gateurl.endsWith('/gate/')){
       gateurl = gateurl.replace('/gate','');
      }
      tempUrl = gateurl;
    }
    else{
      tempUrl = window.location.origin;
    }
    setIsdUrl(tempUrl)
    if (applicationId != undefined) {
      REST('autopilot/api/v1/applications/' + applicationId + '/logTemplates').
        get()
        .then(
          function (results) {
            const response = results['logTemplates'];
            if(modeType === 'add'){
              const selectItem = response.filter(({ templateId : id1 }) => !logDropdownList.some(({ templateId: id2 }) => id2 === id1));
              if (selectItem && selectItem?.length > 0) {
                props.stage.parameters.logTemplate = selectItem[0].templateName;
                setLogTemplate(selectItem[0].templateName);
                setModeType(null);
              }
            }
            setLogDropdownList(response);
          }
        );
    } else {
      REST('platformservice/v2/applications/name/' + props.application['applicationName']).
        get()
        .then(
          (results) => {
            setApplicationId(results.applicationId);
            REST('autopilot/api/v1/applications/' + results.applicationId + '/logTemplates').
              get()
              .then(
                function (results) {
                  const response = results['logTemplates'];
                  if(modeType === 'add'){
                    const selectItem = response.filter(({ templateId : id1 }) => !logDropdownList.some(({ templateId: id2 }) => id2 === id1));
                    if (selectItem && selectItem?.length > 0) {
                      props.stage.parameters.logTemplate = selectItem[0].templateName;
                      setLogTemplate(selectItem[0].templateName);
                      setModeType(null);
                    }
                  }
                  setLogDropdownList(response);
                }
              );
            let logCreateUrl = tempUrl + "/ui/plugin-isd/log-template/" + props.application['applicationName'] + "/" + results.applicationId + "/null/" + props.application.attributes.email + "/false/write/fromPlugin";
            setLogCreateUrl(logCreateUrl);
          }
        )
    }
  }, [logListUpdated])

  useEffect(() => {
    if (!props.stage.hasOwnProperty('parameters')) {
      props.stage.parameters = {}
    }
    if (!props.stage.parameters.hasOwnProperty('environment')) {
      props.stage.parameters.environment = [{
        "id": null,
        "spinnakerEnvironment": ""
      }]
    }

    if(!props.stage.parameters.hasOwnProperty('canaryRealTime')){
      props.stage.parameters.canaryRealTime = false;
    }
    if(!props.stage.parameters.hasOwnProperty('baselineRealTime')){
      props.stage.parameters.baselineRealTime = false;
    }
    // if(!props.stage.parameters.hasOwnProperty('customEnvironment')){
    //   props.stage.parameters.customEnvironment = "";
    // }

    REST('oes/accountsConfig/spinnaker/environments').
      get()
      .then(
        (results) => {
          console.log(results);
          let temp = results;
          temp.unshift({
            "id": 0,
            "spinnakerEnvironment": "+ Add New Environment"
          });
          setenvironmentsList(results);
          if (props.stage.parameters.environment[0].id == 0 && props.stage.parameters.customEnvironment.length > 0) {
            //Find Id from Environment list
            const findId = temp.findIndex((val: any) => (val.spinnakerEnvironment).toLowerCase() == (props.stage.parameters.customEnvironment).toLowerCase());
            if (findId > 0) {
              props.stage.parameters.environment[0].id = temp[findId].id;
              props.stage.parameters.environment[0].spinnakerEnvironment = temp[findId].spinnakerEnvironment;
            }
          }
          console.log("Environmen API: ", temp);
        }
      )
      
      getGateSecurityParams();

  }, [])

  // const pushNewEnvironment = (data: any) => {
  //     setnewEnvironment(data);
  //     props.stage.parameters.customEnvironment = data;
  // }

  

  // Environments 
  const handleOnEnvironmentSelect = (e: any, formik: any) => {
    if (e.target.value === 0) {
      setshowEnvironment(true);
      props.stage.parameters.environment[0].id = 0;
      props.stage.parameters.environment[0].spinnakerEnvironment = '+ Add New Environment';
    } else {
      setshowEnvironment(false);
      props.stage.parameters.customEnvironment = "";
      const index = e.target.value;
      const spinnValue = environmentsList.filter((e: any) => e.id == index)[0].spinnakerEnvironment;
      formik.setFieldValue("parameters.environment[0]['id']", index);
      formik.setFieldValue("parameters.environment[0]['spinnakerEnvironment']", spinnValue);
      //   formik.setFieldValue("parameters.environment]", [{
      //   "id": index,
      //   "spinnakerEnvironment": spinnValue
      // }]); 
    }
  }


  const ANALYSIS_TYPE_OPTIONS: any = [
    { label: 'True', value: 'true' },
    { label: 'False', value: 'false' },
  ];

  const [chosenStage] = React.useState({} as IStageForSpelPreview);


  const setModalIsOpenToTrue = (type: any) => {
    onmetricListUpdated(false);
    if (type == 'add') {
      setModeType(type);
      setMetricUrl(metricCreateUrl);
    } else {
      let editUrl = isdUrl + "/ui/plugin-isd/metric-template/" + props.application['applicationName'] + "/" + applicationId + "/" + props.stage.parameters.metricTemplate + "/{}/" + props.application.attributes.email + "/-1/true/false/fromPlugin";
      setMetricUrl(editUrl);
    }
    setModalIsOpen(true);
  }

  const setModalIsOpenToFalse = () => {
    setModalIsOpen(false);
    onmetricListUpdated(true);
  }

  const setLogModalIsOpenToTrue = (type: any) => {
    onlogListUpdated(false);
    if (type == 'add') {
      setModeType(type);
      setLogUrl(logCreateUrl);
    } else {
      let editUrl = isdUrl + "/ui/plugin-isd/log-template/" + props.application['applicationName'] + "/" + applicationId + "/" + props.stage.parameters.logTemplate + "/" + props.application.attributes.email + "/true/write/fromPlugin";
      setLogUrl(editUrl);
    }
    setLogModalIsOpen(true);
  }

  const setLogModalIsOpenToFalse = () => {
    setLogModalIsOpen(false);
    onlogListUpdated(true);
  }

  const deleteTemplate = (type: any) => {
    if (type == "log") {
      onlogListUpdated(false);
      setDeleteLogModalIsOpen(true);
    } else if (type == "metric") {
      onmetricListUpdated(false);
      setDeleteMetricModalIsOpen(true);
    }
  }

  const setDeleteMetricPopupFalse = () => {
    setDeleteMetricModalIsOpen(false);
  }

  const setDeleteLogPopupFalse = () => {
    setDeleteLogModalIsOpen(false);
  }

  const onMetricTemplateDeleteClick = () => {
    REST('autopilot/api/v1/applications/' + applicationId + "/deleteMetricTemplate/" + props.stage.parameters.metricTemplate).
      delete()
      .then(
        (results) => {
          console.log("metricDelete");
          console.log(results);
          setMetricTemplate(null);
          props.stage.parameters.metricTemplate = null;
          onmetricListUpdated(true);
          setDeleteMetricModalIsOpen(false);
        }
      )
  }

  const onLogTemplateDeleteClick = () => {
    REST('autopilot/api/v1/applications/' + applicationId + "/deleteLogTemplate/" + props.stage.parameters.logTemplate).
      delete()
      .then(
        (results) => {
          console.log("logDelete");
          console.log(results);
          setLogTemplate(null);
          props.stage.parameters.logTemplate = null;
          onlogListUpdated(true);
          setDeleteLogModalIsOpen(false);
        }
      )
  }


  const onChangeLogTemplate = (e: any) => {
    props.stage.parameters.logTemplate = e.target.value;
    setLogTemplate(e.target.value);
  }

  const onChangeMetricTemplate = (e: any) => {
    props.stage.parameters.metricTemplate = e.target.value;
    setMetricTemplate(e.target.value);
  }

  const onCheckBaselineRealTimeCheckbox = (e:any, formik: any) => { 
    console.log("oncheck baseline");
    console.log(formik);
    props.stage.parameters.baselinestarttime = null;
    formik.setFieldValue('parameters.baselinestarttime', null);
    props.stage.parameters.baselineRealTime = e.target.checked;
    formik.setFieldValue('parameters.baselineRealTime', e.target.checked);
    setbaselineRealTime(e.target.checked);
  }; 
  
  const onCheckCanaryRealTimeCheckbox = (e:any, formik: any) => { 
   props.stage.parameters.canarystarttime = null;
    formik.setFieldValue('parameters.canarystarttime', null);
    props.stage.parameters.canaryRealTime = e.target.checked;
    formik.setFieldValue('parameters.canaryRealTime', e.target.checked);
    setcanaryRealTime(e.target.checked);
  };

  //mat-focus-indicator btn btn-primary btnColor mat-button mat-button-base
  //mat-button-wrapper
  return (
    <div className="VerificationGateConfig">
      <FormikStageConfig
        {...props}
        onChange={props.updateStage}
        render={({ formik }: IFormikStageConfigInjectedProps) => (

          <div className="flex">
            <div className="grid"></div>
            <div className=" form mainform inputBox_adjust">

              <div className="form-horizontal">
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    Environment * <HelpField id="opsmx.verification.environment" />
                  </div>
                  <div className="col-md-7">
                    <div className="grid-span-2">
                      <FormikFormField
                        name="parameters.environment[0].id"
                        // label="Environment *"
                        //  help={<HelpField id="opsmx.verification.environment" />}
                        // required={true}
                        input={() => (
                          <ReactSelectInput
                            {...props}
                            clearable={false}
                            onChange={(e) => { handleOnEnvironmentSelect(e, formik) }}
                            options={environmentsList.map((item: any) => (
                              {
                                value: item.id,
                                label: item.spinnakerEnvironment
                              }))}
                            value={formik.values.parameters.environment[0].id}
                          />
                        )}
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div className="form-horizontal">
                <div className="form-group">
                  {showEnvironment ? (
                    <>
                      <div className="col-md-3 sm-label-right">
                        Add New Environment<HelpField id="opsmx.verification.customEnvironment" />
                      </div>
                      <div className="col-md-7">
                        <div className="grid-span-2">
                          {/* <TextInput onChange={(e) => {pushNewEnvironment(e.target.value)}} value={newEnvironment} /> */}
                          <FormikFormField
                            name="parameters.customEnvironment"                          
                            input={(props) => (
                              <TextInput
                                {...props}                          
                                name="customenv" id="customenv" value={customenv}
                                onChange={handleInput}
                              />
                            )}
                          />
                        </div>
                      </div>
                    </>) :
                    null
                  }
                </div>
              </div>


              <HorizontalRule />
              <div className="grid-span-4">
                <h4>Template Configuration </h4>
              </div>
              <div className="form-horizontal">
                <div className="form-group">

                  <div className="col-md-3 sm-label-right">
                    Log Template *<HelpField id="opsmx.verification.logTemplate" />
                  </div>
                  <div className="col-md-7">
                    <div className="flex">
                      <div className={`grid-span-2 ${props.stage.parameters.logTemplate != null || logTemplate != null ? "width-60" : "width-85"}`}>
                        <FormikFormField
                          name="parameters.logTemplate"
                          // label="Log Template *"
                          // help={<HelpField id="opsmx.verification.logTemplate" />}
                          input={(props) => (
                            <ReactSelectInput
                              {...props}
                              clearable={true}
                              // onChange={(o: React.ChangeEvent<HTMLSelectElement>) => {
                              //   ...props.formik.setFieldValue('parameters.logTemplate', o.target.value);
                              // }}
                              //onChange={(e) => setLogTemplate(e.target.value)}
                              // options={logDropdownList['logTemplates'] && logDropdownList['logTemplates'].map((template : any) => ({
                              //   label : template.templateName,
                              //   value : template.templateName}))} 
                              onChange={(e) => { onChangeLogTemplate(e) }}
                              options={logDropdownList && logDropdownList.map((template: any) => ({
                                label: template.templateName,
                                value: template.templateName
                              }))}
                            // options={(getDropdown().result || []).map((s) => ({
                            //   label: s.label,
                            //   value: s.value,
                            // }))}
                            //value={...props}
                            //stringOptions={...props}
                            />
                          )}
                        />
                      </div>
                      <div className="grid-span-2">
                        <Modal id="logtemplate-modal" isOpen={logmodalIsOpen} className="modal-popup-verification modal-dialog" overlayClassName="react-modal-custom">
                          <div className="modal-content">
                            <Tooltip value="Open in a new tab" placement="left">            
                              <a href={logUrl} target="_blank" className="open-new-tab"><img src={openInNewTab} alt="logo" width="18px" ></img></a>               
                            </Tooltip>
                            <div className="modal-close close-button pull-right">
                              <button onClick={setLogModalIsOpenToFalse} className="link">
                                <span className="glyphicon glyphicon-remove close-button-popup"></span>
                              </button>
                            </div>
                            <div className="modal-header">
                              <h4 className="modal-title">Log Template</h4>
                            </div>
                            <div className="grid-span-4 modal-body">
                            <iframe id="templateFrame" src={logUrl} title="ISD">  
                              </iframe>
                            </div>
                          </div>
                        </Modal>

                        <Modal id="logtemplate-modal-delete" isOpen={deleteLogModalIsOpen} className="modal-popup-delete modal-content" overlayClassName="react-modal-custom">
                          <div className="modal-content">                            
                            <div className="modal-close close-button pull-right">
                              <button onClick={setDeleteLogPopupFalse} className="link">
                                <span className="glyphicon glyphicon-remove close-button-popup"></span>
                              </button>
                            </div>
                            <div className="modal-header">
                              <h4 className="modal-title">Really Delete Log Template ?</h4>
                            </div>
                            <div className="grid-span-4 modal-body">
                              Are you sure you want to delete the Log Template ?
                            </div>
                            <div className="modal-footer">
                              <button className="btn btn-default" type="button" onClick={setDeleteLogPopupFalse}>Cancel</button>
                              <button className="btn btn-primary" onClick={() => onLogTemplateDeleteClick()}>
                                <span><span className="far fa-check-circle"></span> Delete</span>
                              </button>
                            </div>
                          </div>
                        </Modal>

                        <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => setLogModalIsOpenToTrue('add')}>
                          <span className="glyphicon glyphicon-plus-sign visible-xl-inline" />
                          <Tooltip value="Create LogTemplate">
                            <span className="glyphicon glyphicon-plus-sign hidden-xl-inline" />
                          </Tooltip>
                          <span className="visible-xl-inline"> Create</span>
                        </button>

                        {props.stage.parameters.logTemplate != null || logTemplate != null ? (
                          <>
                            <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => setLogModalIsOpenToTrue('edit')}>
                              <span className="fa fa-cog visible-xl-inline" />
                              <Tooltip value="Edit LogTemplate">
                                <span className="fa fa-cog hidden-xl-inline" />
                              </Tooltip>
                              <span className="visible-xl-inline"> Edit</span>
                            </button>
                            <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => deleteTemplate('log')}>
                              <span className="glyphicon glyphicon-trash visible-xl-inline" />
                              <Tooltip value="Remove LogTemplate">
                                <span className="glyphicon glyphicon-trash hidden-xl-inline" />
                              </Tooltip>
                              <span className="visible-xl-inline"> Remove</span>
                            </button>
                          </>
                        ) :
                          (
                            null
                          )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>



              <div className="form-horizontal">
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    Metric Template * <HelpField id="opsmx.verification.metricTemplate" />
                  </div>
                  <div className="col-md-7">
                    <div className="flex">
                      <div className={`grid-span-2 ${props.stage.parameters.metricTemplate != null || metricTemplate != null ? "width-60" : "width-85"}`}>
                        <FormikFormField
                          name="parameters.metricTemplate"
                          // label="Metric Template *"
                          // help={<HelpField id="opsmx.verification.metricTemplate" />}
                          input={(props) => (
                            <ReactSelectInput
                              {...props}
                              clearable={true}
                              // onChange={(e) => setMetricTemplate(e.target.value)}
                              // onChange={(o: React.ChangeEvent<HTMLSelectElement>) => {
                              //   this.props.formik.setFieldValue('parameters.metricTemplate', o.target.value);
                              // }} 
                              // options={metricDropdownList && metricDropdownList.map((template : any) => ({
                              //   label : template.templateName,
                              //   value : template.templateName}))}
                              onChange={(e) => { onChangeMetricTemplate(e) }}
                              options={metricDropdownList && metricDropdownList.map((template: any) => ({
                                label: template.templateName,
                                value: template.templateName
                              }))}
                            //options={metricDropdownList}
                            //value={...props}
                            //stringOptions={...props}
                            />
                          )}
                        />
                      </div>
                      <div className="grid-span-2">

                        <Modal id="metrictemplate-modal" isOpen={modalIsOpen} className="modal-popup-verification modal-content" overlayClassName="react-modal-custom">
                          <div className="modal-content">
                            <Tooltip value="Open in a new tab" placement="left">            
                              <a href={metricUrl} target="_blank" className="open-new-tab"><img src={openInNewTab} alt="logo" width="18px" ></img></a>               
                            </Tooltip>
                            <div className="modal-close close-button pull-right">
                              <button onClick={setModalIsOpenToFalse} className="link">
                                <span className="glyphicon glyphicon-remove close-button-popup"></span>
                              </button>
                            </div>
                            <div className="modal-header">
                              <h4 className="modal-title">Metric Template</h4>
                            </div>
                            <div className="grid-span-4 modal-body">
                              <iframe id="templateFrame" src={metricUrl} title="ISD">
                              </iframe>
                            </div>
                          </div>
                        </Modal>

                        <Modal id="metrictemplate-modal-delete" isOpen={deleteMetricModalIsOpen} className="modal-popup-delete modal-content" overlayClassName="react-modal-custom">
                          <div className="modal-content">
                            <div className="modal-close close-button pull-right">
                              <button onClick={setDeleteMetricPopupFalse} className="link">
                                <span className="glyphicon glyphicon-remove"></span>
                              </button>
                            </div>
                            <div className="modal-header">
                              <h4 className="modal-title">Really Delete Metric Template ?</h4>
                            </div>
                            <div className="grid-span-4 modal-body">
                              Are you sure you want to delete the Metric Template ?
                            </div>
                            <div className="modal-footer">
                              <button className="btn btn-default" type="button" onClick={setDeleteMetricPopupFalse}>Cancel</button>
                              <button className="btn btn-primary" onClick={() => onMetricTemplateDeleteClick()}>
                                <span><span className="far fa-check-circle"></span> Delete</span>
                              </button>
                            </div>
                          </div>
                        </Modal>

                        <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => setModalIsOpenToTrue('add')}>
                          <span className="glyphicon glyphicon-plus-sign visible-xl-inline" />
                          <Tooltip value="Create MetricTemplate">
                            <span className="glyphicon glyphicon-plus-sign hidden-xl-inline" />
                          </Tooltip>
                          <span className="visible-xl-inline"> Create</span>
                        </button>
                        {props.stage.parameters.metricTemplate != null || metricTemplate != null ? (
                          <>
                            <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => setModalIsOpenToTrue('edit')}>
                              <span className="fa fa-cog visible-xl-inline" />
                              <Tooltip value="Edit MetricTemplate">
                                <span className="fa fa-cog hidden-xl-inline" />
                              </Tooltip>
                              <span className="visible-xl-inline"> Edit</span>
                            </button>

                            <button className="btn btn-sm btn-default" style={{ marginRight: '5px' }} onClick={() => deleteTemplate('metric')}>
                              <span className="glyphicon glyphicon-trash visible-xl-inline" />
                              <Tooltip value="Remove MetricTemplate">
                                <span className="glyphicon glyphicon-trash hidden-xl-inline" />
                              </Tooltip>
                              <span className="visible-xl-inline"> Remove</span>
                            </button>
                          </>
                        ) :
                          (
                            null
                          )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>




              {/* <div className="grid-span-3">
                <FormikFormField
                  name="parameters.gateurl"
                  label="Gate Url"
                  help={<HelpField id="opsmx.verification.gateUrl" />}
                  input={(props) => <TextInput {...props} />}
                />
              </div> */}



              <HorizontalRule />

              <div className="form-horizontal">
                <div className="form-group">

                  <div className="col-md-3 sm-label-right">
                    LifeTime *<HelpField id="opsmx.verification.lifeTimeMinutes" />
                  </div>
                  <div className="col-md-7">
                    <div>
                      <FormikFormField
                        name="parameters.lifetime"
                        // label="LifeTimeHours *"
                        // help={<HelpField id="opsmx.verification.lifeTimeHours" />}
                        required={true}
                        input={(props) => <TextInput {...props} />}
                      />

                    </div>
                  </div>
                  <div className="label-left">Minutes</div>
                </div>
              </div>
              {/* 
              <div className="flex"> */}
              <div className="form-horizontal">
                <div className="form-group">

                  <div className="col-md-3 sm-label-right">
                    Marginal Score *<HelpField id="opsmx.verification.marginalScore" />
                  </div>
                  <div className="col-md-7">
                    <div>
                      <FormikFormField
                        name="parameters.minicanaryresult"
                        // label="Minimum Canary Result *"
                        // help={<HelpField id="opsmx.verification.minimumCanaryResult" />}
                        input={(props) => <TextInput {...props} />}
                        required={true}
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div className="form-horizontal">
                <div className="form-group">

                  <div className="col-md-3 sm-label-right">
                    Pass Score  *<HelpField id="opsmx.verification.passScore" />
                  </div>
                  <div className="col-md-7">
                    <div>
                      <FormikFormField
                        name="parameters.canaryresultscore"
                        // label="Canary Result Score *"
                        // help={<HelpField id="opsmx.verification.canaryResultScore" />}
                        input={(props) => <TextInput {...props} />}
                        required={true}
                      />
                    </div>
                  </div>
                  {/* </div> */}
                </div>
              </div>

              <div className="form-horizontal">
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    Baseline RealTime<HelpField id="opsmx.verification.baselineRealTime" />
                  </div>
                  <div className="col-md-7">
                    <div className="grid-span-2 checkox-class">
                    <input name="parameters.baselineRealTime" id="parameters.baselineRealTime" type="checkbox" checked={props.stage.parameters.baselineRealTime} onChange={(e) => onCheckBaselineRealTimeCheckbox(e,formik)}  /> 
                    </div>
                  </div>
                </div>
              </div>

              {
                props.stage.parameters.baselineRealTime ? null : 
                  (
                    <>
                      <div className="form-horizontal">
                        <div className="form-group">
                          <div className="col-md-3 sm-label-right">
                            Baseline StartTime *<HelpField id="opsmx.verification.baselineStartTime" />
                          </div>
                          <div className="col-md-7">
                            <div className="grid-span-2">
                              <FormikFormField
                                name="parameters.baselinestarttime"
                                // label="Baseline StartTime *"
                                // help={<HelpField id="opsmx.verification.baselineStartTime" />}
                                // required={true}
                                input={(props) =>
                                  <DateTimePicker {...props} disabled = {baselineRealTime} />}
                              />
                            </div>
                          </div>
                        </div>
                      </div>
                    </>
                  )
              }



              <div className="form-horizontal">
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    Canary RealTime<HelpField id="opsmx.verification.canaryRealTime" />
                  </div>
                  <div className="col-md-7">
                    <div className="grid-span-2 checkox-class">
                    <input name="parameters.canaryRealTime" id="parameters.canaryRealTime" type="checkbox" checked={props.stage.parameters.canaryRealTime} onChange={(e) => onCheckCanaryRealTimeCheckbox(e, formik)}  /> 
                    </div>
                  </div>
                </div>
              </div>

              {
                props.stage.parameters.canaryRealTime ? null : (
                  <>
                    <div className="form-horizontal">
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Canary StartTime *<HelpField id="opsmx.verification.canarystarttime" />
                        </div>
                        <div className="col-md-7">
                          <div className="grid-span-2">
                            <FormikFormField
                              name="parameters.canarystarttime"
                              // label="Canary StartTime *"
                              // help={<HelpField id="opsmx.verification.canarystarttime" />}
                              required={true}
                              input={(props) => <DateTimePicker {...props} disabled={canaryRealTime} />}
                            />
                          </div>
                        </div>
                      </div>
                    </div>
                  </>
                ) 
              }
            </div>
            <div className="opsmxLogo">
              <img
                 src={opsMxLogo}
                alt="logo"
              ></img>
            </div>
          </div>
        )}
      />
    </div>
  );
}

export function validate(stageConfig: IStage) {
  const validator = new FormValidator(stageConfig);

  validator
    .field('parameters.lifetime','Lifetime Minutes')
    .required()
    .withValidators((value, label) => (value = '' ? `LifeTimeMinutes is required` : undefined));

  validator
    .field('parameters.minicanaryresult','Marginal Score')
    .required()
    .withValidators((value, label) => (value = '' ? `Minimum Canary Result is required` : undefined));

  validator
    .field('parameters.canaryresultscore','Pass Score')
    .required()
    .withValidators((value, label) => (value = '' ? `Canary Result Score is required` : undefined));

  validator
    .field('parameters.metricTemplate','Metric Template')
    .required()
    .withValidators((value, label) => (value = '' ? `Metric Template is required` : undefined));

  validator
    .field('parameters.logTemplate','Log Template')
    .required()
    .withValidators((value, label) => (value = '' ? `Log Template is required` : undefined));

  validator
    .field('parameters.environment[0].id','Environment')
    .required()
    .withValidators((value, label) => (value = '' ? `Environment is required` : undefined));


  if(!stageConfig.parameters.baselineRealTime){
    validator
    .field('parameters.baselinestarttime','Baseline Start time')
    .required()
    .withValidators((value)=> (value == NaN ? 'Baseline Start time is required' : '' ))
  }

  if(!stageConfig.parameters.canaryRealTime){
    validator
    .field('parameters.canarystarttime','Canary Start time')
    .required()
    .withValidators((value)=> ( value == NaN ? 'Canary Start time is required' : '' ))
  }

  return validator.validateForm();
}
