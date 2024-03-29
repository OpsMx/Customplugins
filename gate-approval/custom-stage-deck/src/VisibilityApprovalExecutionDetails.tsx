import React, { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import Modal from 'react-modal';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps, SETTINGS, StageFailureMessage, Tooltip } from '@spinnaker/core';
import './VisibilityApproval.less';
import opsMxLogo from './images/OpsMx_logo_Black.svg';
import openInNewTab from './images/open-new-tab-bold.png';
import { ApprovalRequestModal } from './ApprovalRequestModal';

/*
 * You can use this component to provide information to users about
 * how the stage was configured and the results of its execution.
 *
 * In general, you will access two properties of `props.stage`:
 * - `props.stage.outputs` maps to your SimpleStage's `Output` class.
 * - `props.stage.context` maps to your SimpleStage's `Context` class.
 */

export function VisibilityApprovalExecutionDetails(props: IExecutionDetailsSectionProps) {
  console.log("Approval Gate Execution");
  console.log(props);

  let isdUrl =  '';
  const [modalIsOpen,setModalIsOpen] = useState(false);
  const [approvalStatusPopup,setApprovalStatusPopup] = useState(false);
  const [approvalUrl, setApprovalUrl] = useState('');

  useEffect(()=>{
    if(window && window.uiUrl){
      isdUrl = window.uiUrl;
    }
    else if(SETTINGS.gateUrl && (SETTINGS.gateUrl !="/gate/" && SETTINGS.gateUrl !="/gate")){
      let gateurl = SETTINGS.gateUrl;
      if(gateurl.endsWith('/gate') || gateurl.endsWith('/gate/')){
       gateurl = gateurl.replace('/gate','');
      }
      isdUrl = gateurl;
    }
    else{
      isdUrl = window.location.origin;
    }
    if(props.stage.outputs.navigationalURL !== undefined){
      if(props.stage.outputs.navigationalURL.startsWith('http')){
        let modifiedUrl = props.stage.outputs.navigationalURL.replace(/^http[s]?:\/\/.+?\//, '/');
        setApprovalUrl(`${isdUrl}${modifiedUrl}`)
      }else{
        setApprovalUrl(`${isdUrl}${props.stage.outputs.navigationalURL}`)
      }
    }

  },[props.stage.outputs.navigationalURL])

  const getClasses = () => {
    let classes = '';
    if (props.stage.outputs.status == 'approved') {
      classes = 'approvalStatusSuccess';
    } else if (props.stage.outputs.status == 'rejected') {
      classes = 'approvalStatusDanger';
    }
    return classes;
  };

  const getStatus = () => {
    let classes = '';
    if (props.stage.outputs.status == 'approved') {
      classes = 'Approved';
    } else if (props.stage.outputs.status == 'rejected') {
      classes = 'Rejected';
    }
    return classes;
  };
  const exceptionDiv = props.stage.outputs.exception ? (
    <div className="alert alert-danger">
      <div>
        <h5>Exception </h5>
        <div className="Markdown break-word">
          <p>{props.stage.outputs.exception}</p>
        </div>
      </div>
    </div>
  ) : null;

  const setModalIsOpenToTrue =()=>{
      setModalIsOpen(true)
  }

  const setModalIsOpenToFalse =()=>{
      setModalIsOpen(false);      
  }

    const openApprovalDetails =()=>{
      setApprovalStatusPopup(true)
  }

  const closeApprovalDetails =()=>{
      setApprovalStatusPopup(false);      
  }

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      {props.stage.outputs.exception == undefined && props.stage.outputs.status !== undefined ? (
        <div>
          <div className="detailpagelogo">

          <table className="table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Comment</th>
                <th>Last Updated</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <span className={'approvalStatusSmall ' + getClasses()}>{getStatus()}</span>
                </td>
                <td>
                  <div className={'approvalCommentSection'}>{props.stage.outputs.comments}</div>
                </td>
                <td>{new Date(props.stage.endTime).toLocaleString()}</td>
                <td>
                    <span className={'clikable approvalStatusSmall ' + getClasses()} onClick={openApprovalDetails}>View</span>
                    <Modal id="approval-exe-modal2" isOpen={approvalStatusPopup} className="modal-popup-approval modal-dialog" overlayClassName="react-modal-custom">
                      <div className="modal-content">
                        <Tooltip value="Open in a new tab" placement="left">
                          <a href={approvalUrl} target="_blank" className="open-new-tab"><img src={openInNewTab} alt="logo" width="18px" ></img></a>               
                        </Tooltip>
                        <div className="modal-close close-button pull-right">
                          <button onClick={closeApprovalDetails} className="link">
                            <span className="glyphicon glyphicon-remove close-button-popup"></span>
                          </button>
                        </div>
                        <div className="modal-header"> 
                          <h4 className="modal-title">Approval Details</h4>
                        </div>                                      
                        <div className="grid-span-4 modal-body">
                        <iframe id="templateFrame" src={approvalUrl} title="ISD">
                        </iframe>
                        </div>                    
                      </div>
                    </Modal>    
                  </td>
              </tr>
            </tbody>
          </table>
            <img
              src={opsMxLogo}
              alt="logo"
              width="70px"
            ></img>
          </div>
          {exceptionDiv}
        </div>
      ) : props.stage.outputs.exception !== undefined? (
        <div>
          <div className="detailpagelogo">
          </div>
          {exceptionDiv}
        </div>
      ) : props.stage.outputs.navigationalURL !== undefined? (
        <div>
          <div className="detailpagelogo">

            <a className='activeBtn'  onClick={setModalIsOpenToTrue}>
              View Approval Request
            </a>

            <ApprovalRequestModal approvalUrl={approvalUrl} stage={props.stage} modalOpen={modalIsOpen} setModalIsOpenToFalse={setModalIsOpenToFalse} />

            {/* <Modal id="approval-exe-modal" isOpen={modalIsOpen} className="modal-popup-approval modal-dialog" overlayClassName="react-modal-custom">
              <div className="modal-content">
              <Tooltip value="Open in a new tab" placement="left">
                <a href={props.stage.outputs.navigationalURL} target="_blank" className="open-new-tab"><img src={openInNewTab} alt="logo" width="18px" ></img></a>               
              </Tooltip>
                <div className="modal-close close-button pull-right">
                  <button onClick={setModalIsOpenToFalse} className="link">
                    <span className="glyphicon glyphicon-remove close-button-popup"></span>
                  </button>
                </div>
                <div className="modal-header">
                  <h4 className="modal-title"> Approval Request</h4>
                </div>                                      
                <div className="grid-span-4 modal-body">
                <iframe id="templateFrame" src={props.stage.outputs.navigationalURL} title="ISD">
                </iframe>
                </div>                    
              </div>
            </Modal>     */}


          </div>
        </div>
      ) :(
        <>
          {' '}
          <img
            src={opsMxLogo}
            alt="logo"
            width="80px"
            style={{ float: 'right', marginBottom: '10px' }}
          ></img>
          <StageFailureMessage stage={props.stage} message={props.stage.failureMessage} />
        </>
      )}
    </ExecutionDetailsSection>
  );
}

// The title here will be used as the tab name inside the
// pipeline stage execution view. Camel case will be mapped
// to space-delimited text: randomWait -> Random Wait.
export namespace VisibilityApprovalExecutionDetails {
  export const title = 'Approval';
}
