import {CLIENT_VERSION} from "App";
import SafeAnchor from "components/commons/SafeAnchor";
import {renderSidebarRoutes, Sidebar, SidebarIcon} from "components/commons/sidebar-commons";
import {AppContext} from "context";
import React, {useContext} from 'react';
import {FaAt, FaColumns, FaSlidersH, FaTags, FaUserLock, FaUsersCog} from "react-icons/all";
import {UiCol} from "ui/grid";

const AdminSidebar = ({currentNode, reRouteTo}) => {
    const routes = [
        {general: data => <React.Fragment><SidebarIcon as={FaSlidersH} style={data}/> General</React.Fragment>},
        {tags: data => <React.Fragment><SidebarIcon as={FaTags} style={data}/> Tags</React.Fragment>},
        {social: data => <React.Fragment><SidebarIcon as={FaAt} style={data}/> Social Links</React.Fragment>},
        {webhooks: data => <React.Fragment><SidebarIcon as={FaColumns} style={data}/> Webhooks</React.Fragment>},
        {moderators: data => <React.Fragment><SidebarIcon as={FaUsersCog} style={data}/> Moderators</React.Fragment>},
        {suspended: data => <React.Fragment><SidebarIcon as={FaUserLock} style={data}/> Suspensions</React.Fragment>}
    ];
    const context = useContext(AppContext);
    const themeColor = context.getTheme();

    return <UiCol xs={12} md={3} as={Sidebar} theme={themeColor.toString()}>
        <ul>{renderSidebarRoutes(routes, themeColor, currentNode, reRouteTo)}</ul>
        <small className={"text-black-60"}>
            <div>
                Running <img alt={"Project Logo"} src={"https://cdn.feedbacky.net/static/img/logo.png"} width={16} height={16}/> <SafeAnchor url={"https://feedbacky.net"} className={"text-black-60"}>Feedbacky</SafeAnchor>
                <span className={"text-black-75"}>{" "}v{CLIENT_VERSION}</span>
            </div>
            <div>Server Version <span className={"text-black-75"}>v{context.serviceData.serverVersion}</span></div>
        </small>
    </UiCol>
};

export default AdminSidebar;