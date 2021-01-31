import axios from "axios";
import DangerousActionModal from "components/commons/DangerousActionModal";
import SafeAnchor from "components/commons/SafeAnchor";
import AppContext from "context/AppContext";
import BoardContext from "context/BoardContext";
import IdeaContext from "context/IdeaContext";
import React, {useContext, useState} from "react";
import {UiElementDeleteButton} from "ui/button";
import {UiImage} from "ui/image";
import {toastError, toastSuccess} from "utils/basic-utils";

const AttachmentsInfo = () => {
    const {user} = useContext(AppContext);
    const {moderators} = useContext(BoardContext).data;
    const {ideaData, updateState} = useContext(IdeaContext);
    const [modal, setModal] = useState({open: false, data: -1, dataUrl: ""});
    if (ideaData.attachments.length === 0) {
        return <React.Fragment/>
    }
    const onAttachmentDelete = () => {
        axios.delete("/attachments/" + modal.data).then(res => {
            if (res.status !== 204) {
                toastError();
                return;
            }
            updateState({...ideaData, attachments: ideaData.attachments.filter(data => data.url !== modal.dataUrl)});
            toastSuccess("Attachment removed.");
        }).catch(err => {
            toastError(err.response.data.errors[0]);
        });
    };
    //todo lightbox for attachments
    return <React.Fragment>
        <DangerousActionModal id={"attachmentDel"} onHide={() => setModal({...modal, open: false})} isOpen={modal.open} onAction={onAttachmentDelete}
                              actionDescription={<div>Attachment will be permanently <u>deleted</u>.</div>}/>
        <div className={"my-1 text-black-75"}>Attached Files</div>
        {ideaData.attachments.map(attachment => {
            let userId = user.data.id;
            if (ideaData.user.id === userId || moderators.find(mod => mod.userId === userId)) {
                return <React.Fragment key={attachment.id}>
                    <UiElementDeleteButton tooltipName={"Remove"} id={"attachment-del"} onClick={() => setModal({open: true, data: attachment.id, dataUrl: attachment.url})}/>
                    <SafeAnchor url={attachment.url}>
                        <UiImage className={"img-thumbnail"} src={attachment.url} alt={"Social Icon"} width={125}/>
                    </SafeAnchor>
                </React.Fragment>
            }
            return <SafeAnchor key={attachment.id} url={attachment.url}>
                <img width={125} className={"img-thumbnail"} alt={"attachment"} src={attachment.url}/>
            </SafeAnchor>
        })}
    </React.Fragment>
};

export default AttachmentsInfo;