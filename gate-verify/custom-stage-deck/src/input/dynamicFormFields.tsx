import React from 'react';
import {
  FormikFormField,
  IFormikStageConfigInjectedProps,
  ILayoutProps,
  IStageForSpelPreview,
  LayoutContext,
  TextInput,
  Tooltip,
  useIsMountedRef,
  ValidationMessage,
  HelpContentsRegistry,
  HelpField,
  ReactSelectInput,
  IStage
} from '@spinnaker/core';
import { FieldArray, FormikProvider } from 'formik';


interface IEvaluateVariablesStageFormProps extends IFormikStageConfigInjectedProps {
  chosenStage: IStageForSpelPreview;
  headers: [];
  blockLabel: string;
  isMultiSupported: boolean;
  selectInput: boolean;
  options: [];
  connectorsList: [];
  accountsList: [];
  handleOnSelection: any ;
  parentIndex: number;
  fieldMapName: string;
  stage :IStage;
}
export function EvaluateVariablesStageForm(props: IEvaluateVariablesStageFormProps) {
  const { stage, formik, headers, blockLabel, isMultiSupported, parentIndex, selectInput, connectorsList, accountsList,  handleOnSelection, fieldMapName } = props;
  //const stage = props.formik.values;
  const parameters: any = stage?.parameters ?? null;
    const keyParameters: any = fieldMapName == 'gateSecurity' ? parameters.gateSecurity : (fieldMapName == 'connectors' ? parameters.connectors :[]);

  // const variables: any = stage?.parameters?.connectors[parentIndex]?.values ?? [];
  const isMountedRef = useIsMountedRef();
  const emptyValue = (() => {
    const obj: any = {};
    headers.forEach((header: any) => {
      obj[header.name] = null;
    });
    return obj;
  })();
  React.useEffect(() => {
    const values = keyParameters[parentIndex].values;
    if ( !values || values.length === 0 ) {    
      // This setTimeout is necessary because the interaction between pipelineConfigurer.js and stage.module.js
      // causes this component to get mounted multiple times.  The second time it gets mounted, the initial
      // variable is already added to the array, and then gets auto-touched by SpinFormik.tsx.
      // The end effect is that the red validation warnings are shown immediately when the Evaluate Variables stage is added.
      setTimeout(
        () =>
          isMountedRef.current && formik.setFieldValue(`parameters.${fieldMapName}[${parentIndex}].values`, [emptyValue]),
        100,
      );
    }
  }, [ keyParameters[parentIndex].values]);
  const FieldLayoutComponent = React.useContext(LayoutContext);
  const [deleteCount, setDeleteCount] = React.useState(0);

  



  return (
    <>
      <table>
        <thead>
          <tr>
            {/* {JSON.stringify(specificParams)} */}
            {headers.map((header: any) => {
              HelpContentsRegistry.register(blockLabel + header.name, header.helpText);
              return (
                <th key={header.name}>
                  {header.label} <HelpField id={blockLabel + header.name} />
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {/* <tr>
            <td> */}
              {!selectInput ? 
              
              <FormikProvider value={formik}>
            <FieldArray
              key={deleteCount}
              name={`parameters.${fieldMapName}[${parentIndex}].values`}
              render={(arrayHelpers) => (
                
                <>
                  <FieldLayoutComponent input={null} validation={{ hidden: true } as any} />
                  {keyParameters[parentIndex].values ? keyParameters[parentIndex].values.map((_: any, index: number) => {
                    
                    const onDeleteClicked = () => {
                      setDeleteCount((count) => count + 1);
                      arrayHelpers.handleRemove(index)();
                    };
                    return (
                      <tr key={`${deleteCount}-${index}`}>
                        {headers.map((header: any) => (
                          <td key={`${header.name}-td`}>

                          <FormikFormField
                            name={`parameters.${fieldMapName}[${parentIndex}].values[${index}][${header.name}]`}
                            input={(inputProps) => <TextInput {...inputProps} placeholder={` Enter ${header.label}`} />
                          }
                        layout={VariableNameFormLayout}
                        />
                          </td>
                        ))}
                        {isMultiSupported === true ? (
                          <td className="deleteBtn">
                            <Tooltip value="Remove row">
                              <button className="btn btn-sm btn-default" onClick={onDeleteClicked}>
                                <span className="glyphicon glyphicon-trash" />
                              </button>
                            </Tooltip>
                          </td>
                        ) : null}
                      </tr>
                    );
                  })
                  :
                  []
                  }
                  <tr>
                    {isMultiSupported ? (
                      <td colSpan={headers.length + 1}>
                        <button
                          type="button"
                          className="btn btn-block btn-sm add-new"
                          onClick={arrayHelpers.handlePush(emptyValue)}
                        >
                          <span className="glyphicon glyphicon-plus-sign" />
                          Add row
                        </button>
                      </td>
                    ) : null}
                  </tr>
                </>
              )}
            />
          </FormikProvider>             
              
              
              : 
              
              
              <FormikProvider value={formik}>
            <FieldArray
              key={deleteCount}
              name={`parameters.${fieldMapName}[${parentIndex}].values`}
              render={(arrayHelpers) => (
                
                <>
                  <FieldLayoutComponent input={null} validation={{ hidden: true } as any} />
                  {keyParameters[parentIndex].values.map((_: any, index: number) => {
                    
                    const onDeleteClicked = () => {
                      setDeleteCount((count) => count + 1);
                      arrayHelpers.handleRemove(index)();
                    };
                    return (
                      <tr key={`${deleteCount}-${index}`}>
                        {headers.map((header: any) => (
                          <td key={`${header.name}-td`}>
                            
                            <FormikFormField
                              name={`parameters.${fieldMapName}[${parentIndex}].values[${index}][${header.name}]`}
                              input={(inputProps) => 
                                <ReactSelectInput
                                  {...inputProps}
                                  clearable={false}
                                  options={ header.label === 'Connector' ? connectorsList.map((e:any) => ({
                                    value: e,
                                    label: e
                                  })) : accountsList.map((e:any) => ({
                                    value: e,
                                    label: e
                                  }))}
                                  // value={header.label === 'Connector' ? connectorValue : accountValue}
                                  // value={...props}
                                  // onChange={(e)=> handleOnSelection(e, header.label)}
                                  //stringOptions={...props}
                                  
                                  />
                                  
                            }
                              layout={VariableNameFormLayout}
                            />
                          </td>
                        ))}
                        {isMultiSupported === true ? (
                          <td className="deleteBtn">
                            <Tooltip value="Remove row">
                              <button className="btn btn-sm btn-default" onClick={onDeleteClicked}>
                                <span className="glyphicon glyphicon-trash" />
                              </button>
                            </Tooltip>
                          </td>
                        ) : null}
                      </tr>
                    );
                  })}
                  <tr>
                    {isMultiSupported ? (
                      <td colSpan={headers.length + 1}>
                        <button
                          type="button"
                          className="btn btn-block btn-sm add-new"
                          onClick={arrayHelpers.handlePush(emptyValue)}
                        >
                          <span className="glyphicon glyphicon-plus-sign" />
                          Add row
                        </button>
                      </td>
                    ) : null}
                  </tr>
                </>
              )}
            />
          </FormikProvider>
              
              
              
              }

          
            {/* </td>
          </tr> */}
        </tbody>
      </table>
    </>
  );
}
function VariableNameFormLayout(props: ILayoutProps) {
  const { input, validation } = props;
  const { messageNode, category, hidden } = validation;
  return (
    <div className="flex-container-v margin-between-md">
      {input}
      {!hidden && <ValidationMessage message={messageNode} type={category} />}
    </div>
  );
}
