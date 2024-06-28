import React, { useEffect, useState } from 'react';
import {
  ExecutionDetailsSection,
  ExecutionDetailsTasks,
  FormikFormField,
  FormikStageConfig,
  FormValidator,
  IFormInputProps,
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
  LayoutProvider,
  StandardFieldLayout,
  IFormikStageConfigInjectedProps,
  IStageForSpelPreview,
  ReactSelectInput,
  useData,
  REST,
  IUser,
  AuthenticationService,
} from '@spinnaker/core';
import './VisibilityApproval.less';
import { EvaluateVariablesStageForm } from './input/dynamicFormFields';
import { Multiselect } from 'multiselect-react-dropdown';
import { MultiSelect } from 'react-multi-select-component';
import opsMxLogo from './images/OpsMx_logo_Black.svg'

const HorizontalRule = () => (
  <div className="grid-span-4">
    <hr />
  </div>
);

export function VisibilityApprovalConfig(props: IStageConfigProps) {
  const exceptionDiv = (
    <div className="alert alert-danger">
      <div>
        <h5>Exception </h5>
        <div className="Markdown break-word">
          <p>Parameters is undefined</p>
        </div>
      </div>
    </div>
  );



  //Load Initial Values
  const loggedInUser:IUser = AuthenticationService.getAuthenticatedUser();
  const [parametersPresent, setParametersPresent] = useState(false);

  const [accountsApiCall, setAccountsApiCall] = useState(true);

  //Load List of Connectors Details
  const [listOfConnectors, setListOfConnectors] = useState([]);

  const[customenv,setCustomEnv]=useState('');
  
  const handleInput=(event: any)=>{
      event.preventDefault();
      setCustomEnv(event.target.value.toLowerCase()); 
      props.stage.parameters.customEnvironment = event.target.value.toLowerCase();     
  }

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

    if (!props.stage.parameters.hasOwnProperty('connectors')) {
      props.stage.parameters.connectors = [];
    }

    if (!props.stage.parameters.hasOwnProperty('approvalGroups')) {
      props.stage.parameters.approvalGroups = null;
    }

    if (!props.stage.parameters.hasOwnProperty('automatedApproval')) {
      props.stage.parameters.automatedApproval = [{
        "policyId": null,
        "policyName": ""
      }]
    }

    if(!props.stage?.parameters.hasOwnProperty('gateSecurity')){
      props.stage.parameters.gateSecurity = [
      {
        "connectorType": "PayloadConstraints",
        "helpText": "Payload Constraints",
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
        "values": []
      }
    ]
    }

    REST(`visibilityservice/v6/getAllConnectorFields`).
      get()
      .then(
        (connectors) => {
          setListOfConnectors(connectors)
        }
      );

    REST(`platformservice/v2/applications/name/${props.application['applicationName']}`).
      get()
      .then(
        (application) => {
          props.stage.applicationId = JSON.parse(application.applicationId);

          REST(`platformservice/v4/applications/${props.stage.applicationId}`).
            get()
            .then(
              (results) => {
                getServicePipelineDetails(results);
              }
            )
        }
      )
    setParametersPresent(true);

  }, []);



  const addSelectedConnectorParameters = () => {
    if (!props.stage?.parameters.hasOwnProperty('selectedConnectors')) {
      props.stage.parameters.selectedConnectors = [
        {
          "connectorType": "Connectors *",
          "helpText": "List of Connectors Configured",
          "isMultiSupported": true,
          "label": "Connectors *",
          "selectInput": true,
          "supportedParams": [
            {
              "helpText": "Select Data Sources relevant to this pipeline",
              "label": "Connector",
              "name": "connector",
              "type": "string"
            },
            {
              "helpText": "Select the account of interest in the configured data source ",
              "label": "Account",
              "name": "account",
              "type": "string"
            }
          ],
          "values": [
            {
              "account": "",
              "connector": ""
            }
          ]
        }
      ]
    } else if (accountsApiCall) {
      setAccountsApiCall(false); //Set to false to call API's only once
      props.stage.parameters.selectedConnectors[0].values.forEach((obj: any) => {
        callingAccountsAPI(obj.connector);
      });
    }
  }

  // const getGateSecurityParams = () => {

  // }

  //Add Serivce and Pipeline Details to the JSON
  const getServicePipelineDetails = (data: any) => {
    const pipelineName = props.pipeline.name;
    const index = data.services.findIndex((service: any) => service.serviceName == pipelineName);
    if (index >= 0) {
      props.stage.serviceId = data.services[index].serviceId;
      const pipelines = data.services[index].pipelines;
      const pipelineIndex = pipelines.findIndex((pipeline: any) => pipeline.pipelineName == pipelineName);
      if (pipelineIndex >= 0) {
        props.stage.pipelineId = pipelines[pipelineIndex].pipelineId;
      }
    }
  }

  // Environments
  const [environmentsList, setEnvironmentList] = useState([]);
  const [showEnvironment, setshowEnvironment] = useState(false);

  useEffect(() => {
    REST('oes/accountsConfig/spinnaker/environments').
      get()
      .then(
        (response) => {
          let temp = response;
          temp.unshift({
            "id": 0,
            "spinnakerEnvironment": "+ Add New Environment"
          });
          setEnvironmentList(temp);

          if (props.stage.parameters.environment[0].id == 0 && props.stage.parameters.customEnvironment.length > 0) {

            //Find Id from Environment list
            const findId = temp.findIndex((val: any) => (val.spinnakerEnvironment).toLowerCase() == (props.stage.parameters.customEnvironment).toLowerCase());
            if (findId > 0) {
              props.stage.parameters.environment[0].id = temp[findId].id;
              props.stage.parameters.environment[0].spinnakerEnvironment = temp[findId].spinnakerEnvironment;
            }
          }

        }
      )
  }, [])


  const handleOnEnvironmentSelect = (e: any, formik: any) => {
    if (e.target.value === 0) {
      setshowEnvironment(true);
      props.stage.parameters.environment[0].id = 0;
      props.stage.parameters.environment[0].spinnakerEnvironment = '+ Add New Environment'

    } else {
      setshowEnvironment(false);
      props.stage.parameters.customEnvironment = "";
      const index = e.target.value;
      const spinnValue = environmentsList.filter((e: any) => e.id == index)[0].spinnakerEnvironment;
      formik.setFieldValue("parameters.environment[0]['id']", index);
      formik.setFieldValue("parameters.environment[0]['spinnakerEnvironment']", spinnValue);
    }

  }

  //Automated Approval

  const [policyList, setpolicyList] = useState([]);
  useEffect(() => {
    REST(`oes/v2/policy/users/${loggedInUser.name}/runtime?permissionId=view`).
      get()
      .then(
        (results) => {
          setpolicyList(results);
        }
      )
  }, [])

  const handleAutomatedApproval = (e: any, props: any) => {
    const index = e.target.value;
    const policyName = policyList.filter((e: any) => e.policyId == index)[0].policyName;
    props.formik.setFieldValue("parameters.automatedApproval]", [{
      "policyId": index,
      "policyName": policyName
    }]);
  }


  // Approval Groups
  const [approvalGroupsList, setapprovalGroupsList] = useState([]);
  useEffect(() => {
    REST('platformservice/v2/usergroups').
      get()
      .then(
        (results) => {
          setapprovalGroupsList(results);
        }
      )
  }, [])

  const handleApprovalGroups = (e: any, props: any) => {
    props.formik.setFieldValue(`parameters.approvalGroups`, e)
  }

  const handleOnremoveOption =(e:any,props:any)=>{
    if(e?.length === 0) props.formik.setFieldValue(`parameters.approvalGroups`, null)
    else props.formik.setFieldValue(`parameters.approvalGroups`, e)
  }
  

  // const [checked, setChecked] = useState(false);

  const handleCheckbox = (e: any, props: any) => {
    props.formik.setFieldValue('parameters.isAutomatedApproval', e.target.checked)
  };


  // selectedConnectors 

  const [connectorTypeList, setConnectorTypeList] = useState([]);

  useEffect(() => {
    REST(`visibilityservice/v2/toolConnectors/configuredConnectorTypes`).
      get()
      .then((response) => {
        setConnectorTypeList(response)
      })
  }, [])


  const [selectedConnector, setSelectedConnector] = useState("");
  const [accountsOptions, setAccountsOptions] = useState([])
  const [loadConnectors, setLoadConnectors] = useState(true);

  React.useEffect(() => {

    if (!selectedConnector) {
      return;
    }

    callingAccountsAPI(selectedConnector);






  }, [selectedConnector])


  const callingAccountsAPI = (connectorName: string) => {

    if (!connectorName) {
      return;
    }
    // IgorService.getConnectorAccounts(connectorName, application.attributes.user).then(response => {
    REST(`platformservice/v6/users/${loggedInUser.name}/datasources?datasourceType=${connectorName}&permissionId=view`).
      get()
      .then((response) => {
        if (listOfConnectors && (connectorName.length > 0)) {
          // Find Index of listOfConnectors to see if the selected connector is present
          const findIndex = accountsOptions.findIndex((specificAccountOption: any) => specificAccountOption[connectorName.toLowerCase()] == connectorName.toLowerCase());
          if (findIndex < 0) {
            let temp: any = {};
             // For github connectors, supporting data that has only token  
            if(connectorName.toLowerCase() == 'github') 
              response = response.filter((acc : any) => acc.configurationFields.hasOwnProperty('token'))

            temp[connectorName.toLowerCase()] = response;

            let options = JSON.parse(localStorage.getItem('accountList')) ? JSON.parse(localStorage.getItem('accountList')) : [];
            // Options has connector name do not add
            let keyPresent = options.some((obj: any) => obj.hasOwnProperty(connectorName.toLowerCase()));
            if (!keyPresent) {
              options = [...options, temp];
              localStorage.setItem('accountList', JSON.stringify(options));
            } else {
              // Find the key and update the localStorage and options for Accounts 
              const index = options.findIndex((obj: any) => obj.hasOwnProperty(connectorName.toLowerCase()));
              if (index >= 0) {
                options[index][connectorName.toLowerCase()] = response;
                localStorage.setItem('accountList', JSON.stringify(options));
              }
            }
            setAccountsOptions(options);
          }
        }
      });
  }


  React.useEffect(() => {

    setListOfConnectors(listOfConnectors)

    // Check if connectors are present in the Parameters
    if (listOfConnectors.length > 0) {
      // Find the object of selected Connectors in listOfConnectors and push it to connectors
      const indexOfSelectedConnector = listOfConnectors.findIndex((connector: any) => connector.connectorType.toLowerCase() == selectedConnector.toLowerCase());
      if (indexOfSelectedConnector >= 0) {
        // Check if the  connectorType exists in connectors
        const findConnectorIndex = props.stage.parameters.connectors.findIndex((obj: any) => obj.connectorType.toLowerCase() == selectedConnector.toLowerCase());
        if (findConnectorIndex < 0) {
          props.stage.parameters.connectors.push(listOfConnectors[indexOfSelectedConnector]);
        }


        // Display only the selected connectors
        const selectedConnectorsObj = props.stage.parameters.selectedConnectors

        if (selectedConnectorsObj.length > 0 && selectedConnectorsObj[0].values) {
          const selectedArray = [...new Set(selectedConnectorsObj[0].values.map((obj: any) => obj.connector ? obj.connector?.toLowerCase() : null))];
          selectedArray.forEach((element: any, index: number) => {
            if (!element)
              selectedArray.splice(index, 1);
          });

          const connectorsObj = props.stage.parameters.connectors;
          if (connectorsObj && connectorsObj.length > 0) {
            connectorsObj.forEach((obj: any, index: number) => {
              if (!(selectedArray.includes(obj.connectorType.toLowerCase()))) {
                connectorsObj.splice(index, 1);
              }
            })
          }
        }
      }

      setLoadConnectors(false);
      setTimeout(() => {
        setLoadConnectors(true);
      }, 100);
    }

  }, [listOfConnectors, selectedConnector]);

  const handleOnSelection = (e: any, label: string, index: number, props: any) => {

    if (label == "Connector") {
      setSelectedConnector(e.target.value);
      props.formik.setFieldValue(`parameters.selectedConnectors[0].values[${index}].connector`, e.target.value);
    }
  }

  //  Component Starts ------------------------------

  const [chosenStage] = React.useState({} as IStageForSpelPreview);


  
  const renderSupportedParams = (props: IFormikStageConfigInjectedProps) => {

    const fieldParams = props?.formik?.values?.parameters ? props.formik.values.parameters : props?.stage?.parameters ? props.stage.parameters : null;
    if (!(fieldParams && fieldParams.connectors)) {
      return;
    }
    if(fieldParams.selectedConnectors && fieldParams.selectedConnectors[0] && fieldParams.selectedConnectors[0].values?.length>0){
      let selectedConnTypes = [];
      fieldParams.selectedConnectors[0].values.forEach(el=>{
        selectedConnTypes.push(el.connector)
      })
      fieldParams.connectors.sort(function (a, b) {
     return selectedConnTypes.indexOf(a.connectorType) - selectedConnTypes.indexOf(b.connectorType);
   });
    }
    return fieldParams.connectors.map((dynamicField: any, index: number) => {
      if (dynamicField.supportedParams.length < 2 && !dynamicField.isMultiSupported) {
        HelpContentsRegistry.register(
          'approval' + dynamicField.connectorType + dynamicField.supportedParams[0].name,
          dynamicField.supportedParams[0].helpText,
        );
        return (
          <div className="grid-span-2">

            <div className="form-horizontal">
              <div className="form-group">
                <div className="col-md-3 sm-label-right">
                  {((dynamicField.connectorType == 'AUTOPILOT' ? "VERIFICATION" : dynamicField.connectorType) + ' ' + dynamicField.supportedParams[0].label).toUpperCase()}<HelpField id={'approval' + dynamicField.connectorType + dynamicField.supportedParams[0].name} />
                </div>
                <div className="col-md-7">
                  <FormikFormField
                    name={`parameters.connectors[${index}].values[0].${dynamicField.supportedParams[0].name}`}
                    input={(props) => <TextInput {...props} />}
                  />
                </div>
              </div>
            </div>
          </div>
        );
      } else {
        if (
          (dynamicField.supportedParams.length > 0 && dynamicField.isMultiSupported) ||
          dynamicField.supportedParams.length > 1
        ) {
          HelpContentsRegistry.register('approval' + dynamicField.connectorType, dynamicField.helpText);
          return (
            <div className="grid-span-2">
  
              <div className="form-horizontal">
                <div className="form-group">
                  <div className="col-md-3 sm-label-right">
                    {dynamicField.connectorType.toUpperCase()}<HelpField id={'approval' + dynamicField.connectorType} />
                  </div>
                  <div className="col-md-7">
                    <FormikFormField
                      name={dynamicField.connectorType}
                      input={() => (
                        <LayoutProvider value={StandardFieldLayout}>
                          <div className="flex-container-v margin-between-lg dynamicFieldSection">
                            <EvaluateVariablesStageForm
                              blockLabel={dynamicField.connectorType}
                              chosenStage={chosenStage}
                              headers={dynamicField.supportedParams}
                              isMultiSupported={dynamicField.isMultiSupported}
                              fieldMapName="connectors"
                              parentIndex={index}
                              {...props}
                            />
                          </div>
                        </LayoutProvider>
                      )}
                    />
                  </div>
                </div>
              </div>
  
            </div>
          );
        } else {
          return null;
        }
      }
  })
}

  const renderComponent = (props: IFormikStageConfigInjectedProps) => {
    const propsFormik = props.formik.values;
    if (!propsFormik?.parameters) {
      return;
    }
    return <>
      <div>
        <div className="form-horizontal">
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              Environment * <HelpField id="opsmx.approval.environment" />
            </div>
            <div className="col-md-7">
              <FormikFormField
                // name={props.stage.parameters.environment[0].spinnakerEnvironment}
                name="parameters.environment[0].id"
                label=""
                // required={true}
                input={() => (
                  <ReactSelectInput
                    {...props}
                    clearable={false}
                    // value={`parameters.environment[0]['id]`}
                    onChange={(e) => { handleOnEnvironmentSelect(e, props.formik) }}
                    // onChange={(o: React.ChangeEvent<HTMLSelectElement>) => {
                    //   this.props.formik.setFieldValue('parameters.connectorType', o.target.value);
                    // }}
                    // stringOptions={connectorAccountList.map((e) => e.label)} 
                    options={environmentsList.map((item: any) => (
                      {
                        value: item.id,
                        label: item.spinnakerEnvironment
                      }))}
                    //value={...props}
                    value={props.formik.values?.parameters.environment[0].id}
                  // stringOptions={...props}
                  />
                )}
              />
            </div>
          </div>
        </div>

        <div className="form-horizontal">
          <div className="form-group">
            {showEnvironment ? (
              <>
                <div className="col-md-3 sm-label-right">
                  Add New Environment<HelpField id="opsmx.approval.customEnvironment" />
                </div>
                <div className="col-md-7">
                  <div className='grid-span-2'>
                  <FormikFormField
                    name="parameters.customEnvironment"
                    input={(props) => (
                      <TextInput
                        {...props}                          
                        name="customenv" 
                        id="customenv" 
                        value={customenv}
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
      </div>


      {/* Automated Approval */}

      {/*  */}
      <div>
      <div className="form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Automated Approval <HelpField id="opsmx.approval.automatedApproval" />
          </div>
          <div className="col-md-7">

            {/* <p style={{paddingRight:"10px"}}>Automated Approval</p> */}
            <div className="automatedDiv">
              <input type="checkbox" checked={propsFormik?.parameters?.isAutomatedApproval} onChange={(e) => handleCheckbox(e, props)} />


            </div>


          </div>
        </div>
      </div>

      <div className="form-horizontal">
          <div className="form-group">
            {propsFormik?.parameters?.isAutomatedApproval ? (
              <>
                <div className="col-md-3 sm-label-right">
                Approval Condition<HelpField id="opsmx.approval.approvalCondition" />
                </div>
                <div className="col-md-7">
                  <div className='grid-span-2'>
                  <FormikFormField
                    name="parameters.automatedApproval[0].policyId"
                    label=""
                    input={(automatedApproval) => (
                      <ReactSelectInput
                        {...automatedApproval}
                        clearable={false}
                        onChange={(e: any) => handleAutomatedApproval(e, props)}
                        //   this.props.formik.setFieldValue('parameters.connectorType', o.target.value);
                        // }}
                        options={policyList && policyList.map((policy: any) => ({
                          value: policy.policyId,
                          label: policy.policyName
                        }))}
                        value={props.formik.values.parameters?.automatedApproval[0].policyId}
                      //stringOptions={...props}
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

      </div>


      {/*  */}



      {/* Approval Groups */}

      {/*  */}


      <div className='grid grid-2'>
        {/* <div className="grid-span-1"> */}
        {/* <div className='sp-formItem'> */}

        <div className="form-horizontal">
          <div className="form-group">
            {!propsFormik?.parameters?.isAutomatedApproval ? (
              <>
                <div className="col-md-3 sm-label-right">
                  Approver Group * <HelpField id="opsmx.approval.approverGroup" />
                </div>
                <div className="col-md-7">
                  <div className="padding-8">

                    <Multiselect
                      id="parameters.approvalGroups"
                      name="parameters.approvalGroups"
                      options={approvalGroupsList && approvalGroupsList?.map((approvalGroup: any) => ({
                        label: approvalGroup.userGroupName,
                        value: approvalGroup.userGroupId,
                        userGroupName: approvalGroup.userGroupName,
                        userGroupId: approvalGroup.userGroupId,
                        isAdmin: approvalGroup.isAdmin,
                        isSuperAdmin: approvalGroup.isSuperAdmin
                      }))}
                      selectedValues={propsFormik?.parameters.approvalGroups}
                      onSelect={(e: any) => handleApprovalGroups(e, props)}
                      onRemove={(e: any) => handleOnremoveOption(e, props)}
                      displayValue="label"
                      showCheckbox={true}
                      showArrow
                      style={{
                        multiSelectContainer: {
                          height: '200px !important',
                          width: '50%'
                        },
                        searchBox: { // To change search box element look

                        },
                        option: { // To change css for dropdown options
                          display: 'flex',
                          alignItems: 'center',
                          padding: '6px'
                        },
                        optionContainer: {
                          maxHeight: '200px !important'
                        }
                      }}
                    />




                  </div>
                </div>
              </>) :
              null}
          </div>
        </div>
        {/* </div>
        </div> */}

      </div>


      {/*  */}


      <HorizontalRule />


      <div className='grid grid-span-2'>
        {/* Load selectedConnectors */}
        {multiFieldConnectorComp({ ...props })}
      </div>

      <div className="grid grid-4"></div>

      {/* Load Connector Specific Details */}
      <div className='grid grid-2'>
        {loadConnectors ? renderSupportedParams(props) : null}
      </div>

    </>
   
  };


  //MultiField Selected Connectors dropdown

  const multiFieldConnectorComp = (props: any) => {
    addSelectedConnectorParameters();
    const fieldParams = props.formik.values.parameters ?? null
    if (!fieldParams) {
      return;
    }
    return fieldParams.selectedConnectors.map((dynamicField: any, index: number) => {
      if (
        (dynamicField?.supportedParams.length > 0 && dynamicField?.isMultiSupported) ||
        dynamicField?.supportedParams.length > 1
      ) {

        HelpContentsRegistry.register(dynamicField.connectorType, dynamicField.helpText);
        return (
          <div className="grid-span-2">
            <div className="form-horizontal">
              <div className="form-group">
                <div className="col-md-3 sm-label-right">
                <p style={{fontSize:"16px"}}>Connector Configuration <HelpField id={dynamicField.connectorType} /></p>
                </div>
                <div className="col-md-7">
                  <FormikFormField
                    name={dynamicField.connectorType}
                    // label={dynamicField.connectorType}
                    // help={<HelpField id={dynamicField.connectorType} />}
                    input={() => (
                      <LayoutProvider value={StandardFieldLayout}>
                        <div className="flex-container-v margin-between-lg dynamicFieldSection">
                          {/* <div>
                      accountsOptions: {JSON.stringify(accountsOptions)}
                    </div> */}
                          <EvaluateVariablesStageForm
                            blockLabel={dynamicField.connectorType}
                            chosenStage={chosenStage}
                            headers={dynamicField.supportedParams}
                            selectInput={true}
                            connectorValue={selectedConnector}
                            connectorsList={connectorTypeList}
                            // accountValue={accountValue}
                            accountsOptions={accountsOptions}
                            listOfConnectorDetails={listOfConnectors}
                            handleOnSelection={handleOnSelection}
                            fieldMapName="selectedConnectors" // to read and map fields
                            isMultiSupported={dynamicField.isMultiSupported}
                            parentIndex={index}
                            {...props}
                          />
                        </div>
                      </LayoutProvider>
                    )}
                  />
                </div>
              </div>
            </div>



          </div>
        );
      } else {
        return null;
      }
    });
  };


  return (
    <div className="VisibilityApprovalConfig">
      <FormikStageConfig
        {...props}
        onChange={props.updateStage}
        render={(props: IFormikStageConfigInjectedProps) => (
          <div>
            <div className="">

              <div className="opsmxLogo">
                <img
                  src={opsMxLogo}
                  alt="logo"
                ></img>
              </div>

              <div className="grid leftGrid"></div>
              {/* <div className="grid grid-4 mainForm">{renderComponent({ ...props })}</div> */}
              <div>{renderComponent({ ...props })}</div>
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
    .field('parameters.environment[0].id','Environment')
    .required("Environment is required")
    .withValidators((value, label) => (value = '' ? `Environment is required` : undefined));
    
  if (!stageConfig.parameters.isAutomatedApproval) {
    validator
      .field('parameters.approvalGroups', 'Approver Group')
      .required("Approver group is required. If Approver group is not entered anybody can able to approve or reject the application.")
      .withValidators((value, label) => (value = '' ? `Approver group is required. If Approver group is not entered anybody can able to approve or reject the application.` : undefined));
  }

    stageConfig.parameters.selectedConnectors[0].values?.map((connectorValue: any, index:number) => {
        validator
        .field(`parameters.selectedConnectors[0].values[${index}].connector`)
        .optional()
        .withValidators((value)=> {
          if ((Boolean(connectorValue.connector) && Boolean(connectorValue.account)) || Boolean(!connectorValue.connector)){
          return ""
          }
          return `${connectorValue.connector == 'AUTOPILOT'  ? "VERIFICATION" : connectorValue.connector} Account is required`
        })
  });

    stageConfig.parameters.connectors.map((connector: any, index: number) => {
     
      if (connector.values?.length > 1) {
        connector.values.map((connectorValue: any, valueIndex: number) => {
          if (connector.supportedParams?.length > 1) {
            connector.supportedParams.map((param: any, paramIndex: number) => {
              {
                validator
                  .field(`parameters.connectors[${index}].values[${valueIndex}].${connector.supportedParams[paramIndex].name}`)
                  .required(`${connector.connectorType == 'AUTOPILOT' ? "VERIFICATION" : connector.connectorType + ' ' + connector.supportedParams[paramIndex].label}  is required for row ${valueIndex+1}`);
  
              }
            });
          }
        })
      } else if(connector.supportedParams?.length > 1){
        connector.supportedParams.map((param: any, paramIndex: number) => {
          {
            validator
              .field(`parameters.connectors[${index}].values[0].${connector.supportedParams[paramIndex].name}`)
              .required(`${connector.connectorType == 'AUTOPILOT' ? "VERIFICATION" : connector.connectorType + ' ' + connector.supportedParams[paramIndex].label} is required.`);
  
          }
        });
      }
      else {
        validator
          .field(`parameters.connectors[${index}].values[0].${connector.supportedParams[0].name}`)
          .required(`${(connector.connectorType == 'AUTOPILOT' ? 'VERIFICATION' : connector.connectorType) + ' ' + connector.supportedParams[0].label} is required`);
      }
    });



    
 return validator.validateForm();
}
