import React from 'react';
import { ExecutionDetailsTasks, IStageTypeConfig } from '@spinnaker/core';

import { VisibilityApprovalExecutionDetails } from './VisibilityApprovalExecutionDetails';
import { VisibilityApprovalConfig, validate } from './VisibilityApprovalConfig';
import { ApprovalLabel } from './ApprovalLabel';

/*
  Define Spinnaker Stages with IStageTypeConfig.
  Required options: https://github.com/spinnaker/deck/master/app/scripts/modules/core/src/domain/IStageTypeConfig.ts
  - label -> The name of the Stage
  - description -> Long form that describes what the Stage actually does
  - key -> A unique name for the Stage in the UI; ties to Orca backend
  - component -> The rendered React component
  - validateFn -> A validation function for the stage config form.
 */
export const approval: IStageTypeConfig = {
  key: 'approval',
  label: `Approval`,
  description: 'Stage for Approval',
  component: VisibilityApprovalConfig,
  executionDetailsSections: [VisibilityApprovalExecutionDetails, ExecutionDetailsTasks],
  executionLabelComponent: ApprovalLabel,
  supportsCustomTimeout: true,
  useCustomTooltip: true,
  validateFn: validate,
};
