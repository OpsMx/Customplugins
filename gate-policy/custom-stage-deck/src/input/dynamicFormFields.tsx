

import { FieldArray, FormikProvider } from 'formik';
import React from 'react';
import { HelpContentsRegistry, HelpField } from '../../../../../help';
import { FormikFormField, LayoutContext, TextInput, Tooltip, useIsMountedRef, ValidationMessage } from '../../../../../presentation';
import type { IFormikStageConfigInjectedProps } from '../../FormikStageConfig';
import type { ILayoutProps } from '../../../../../presentation';
import type { IStageForSpelPreview } from '../../../../../presentation';

interface IEvaluateVariablesStageFormProps extends IFormikStageConfigInjectedProps {
  chosenStage: IStageForSpelPreview;
  headers: [];
  blockLabel: string;
  isMultiSupported: boolean;
  parentIndex: number;
}
export function EvaluateVariablesStageForm(props: IEvaluateVariablesStageFormProps) {
  const { formik, headers, blockLabel, isMultiSupported, parentIndex } = props;
  const stage = props.formik.values;
  const variables: any = stage?.parameters?.payloadConstraint[parentIndex]?.values ? stage.parameters.payloadConstraint[parentIndex].values : [];
  const isMountedRef = useIsMountedRef();
  const emptyValue = (() => {
    const obj: any = {};
    headers.forEach((header: any) => {
      obj[header.name] = null;
    });
    return obj;
  })();
  React.useEffect(() => {
    if (variables.length === 0) {
      // This setTimeout is necessary because the interaction between pipelineConfigurer.js and stage.module.js
      // causes this component to get mounted multiple times.  The second time it gets mounted, the initial
      // variable is already added to the array, and then gets auto-touched by SpinFormik.tsx.
      // The end effect is that the red validation warnings are shown immediately when the Evaluate Variables stage is added.
      setTimeout(
        () =>
          isMountedRef.current && formik.setFieldValue(`parameters.payloadConstraint[${parentIndex}].values`, [emptyValue]),
        100,
      );
    }
  }, [variables]);
  const FieldLayoutComponent = React.useContext(LayoutContext);
  const [deleteCount, setDeleteCount] = React.useState(0);
  return (
    <>
      <table>
        <thead>
          <tr>
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
          <FormikProvider value={formik}>
            <FieldArray
              key={deleteCount}
              name={`parameters.payloadConstraint[${parentIndex}].values`}
              render={(arrayHelpers) => (
                <>
                  <FieldLayoutComponent input={null} validation={{ hidden: true } as any} />
                  {variables.map((_: any, index: number) => {
                    const onDeleteClicked = () => {
                      setDeleteCount((count) => count + 1);
                      arrayHelpers.handleRemove(index)();
                    };
                    return (
                      <tr key={`${deleteCount}-${index}`}>
                        {headers.map((header: any) => (
                          <td key={`${header.name}-td`}>
                            <FormikFormField
                              name={`parameters.payloadConstraint[${parentIndex}].values[${index}][${header.name}]`}
                              input={(inputProps) => <TextInput {...inputProps} placeholder={` Enter ${header.label}`} />}
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