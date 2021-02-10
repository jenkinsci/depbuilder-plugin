const ICON_STOP=`<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512"><path class="iconColor" d="M400 32H48C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48 48h352c26.5 0 48-21.5 48-48V80c0-26.5-21.5-48-48-48z"></path></svg>`
const ICON_PLAY=`<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512"><path class="iconColor" d="M424.4 214.7L72.4 6.6C43.8-10.3 0 6.1 0 47.9V464c0 37.5 40.7 60.1 72.4 41.3l352-208c31.4-18.5 31.5-64.1 0-82.6z"></path></svg>`
const ICON_RESTART=`<svg class="icon RESTART" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path class="iconColor" d="M256.455 8c66.269.119 126.437 26.233 170.859 68.685l35.715-35.715C478.149 25.851 504 36.559 504 57.941V192c0 13.255-10.745 24-24 24H345.941c-21.382 0-32.09-25.851-16.971-40.971l41.75-41.75c-30.864-28.899-70.801-44.907-113.23-45.273-92.398-.798-170.283 73.977-169.484 169.442C88.764 348.009 162.184 424 256 424c41.127 0 79.997-14.678 110.629-41.556 4.743-4.161 11.906-3.908 16.368.553l39.662 39.662c4.872 4.872 4.631 12.815-.482 17.433C378.202 479.813 319.926 504 256 504 119.034 504 8.001 392.967 8 256.002 7.999 119.193 119.646 7.755 256.455 8z"></path></svg>`
const ICON_PIPELINE = `<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path class="iconColor" d="M504.971 359.029c9.373 9.373 9.373 24.569 0 33.941l-80 79.984c-15.01 15.01-40.971 4.49-40.971-16.971V416h-58.785a12.004 12.004 0 0 1-8.773-3.812l-70.556-75.596 53.333-57.143L352 336h32v-39.981c0-21.438 25.943-31.998 40.971-16.971l80 79.981zM12 176h84l52.781 56.551 53.333-57.143-70.556-75.596A11.999 11.999 0 0 0 122.785 96H12c-6.627 0-12 5.373-12 12v56c0 6.627 5.373 12 12 12zm372 0v39.984c0 21.46 25.961 31.98 40.971 16.971l80-79.984c9.373-9.373 9.373-24.569 0-33.941l-80-79.981C409.943 24.021 384 34.582 384 56.019V96h-58.785a12.004 12.004 0 0 0-8.773 3.812L96 336H12c-6.627 0-12 5.373-12 12v56c0 6.627 5.373 12 12 12h110.785c3.326 0 6.503-1.381 8.773-3.812L352 176h32z"></path></svg>`
const ICON_BAN = `<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path class="iconColor" d="M256 8C119.034 8 8 119.033 8 256s111.034 248 248 248 248-111.034 248-248S392.967 8 256 8zm130.108 117.892c65.448 65.448 70 165.481 20.677 235.637L150.47 105.216c70.204-49.356 170.226-44.735 235.638 20.676zM125.892 386.108c-65.448-65.448-70-165.481-20.677-235.637L361.53 406.784c-70.203 49.356-170.226 44.736-235.638-20.676z"></path></svg>`
const ICON_CANCEL_X = `<svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 352 512"><path class="iconColor" d="M242.72 256l100.07-100.07c12.28-12.28 12.28-32.19 0-44.48l-22.24-22.24c-12.28-12.28-32.19-12.28-44.48 0L176 189.28 75.93 89.21c-12.28-12.28-32.19-12.28-44.48 0L9.21 111.45c-12.28 12.28-12.28 32.19 0 44.48L109.28 256 9.21 356.07c-12.28 12.28-12.28 32.19 0 44.48l22.24 22.24c12.28 12.28 32.2 12.28 44.48 0L176 322.72l100.07 100.07c12.28 12.28 32.2 12.28 44.48 0l22.24-22.24c12.28-12.28 12.28-32.19 0-44.48L242.72 256z"></path></svg>`;


// declaring library functions to satisfy the ts compiler
declare let d3: any;
declare let dagreD3: any;
declare let Ajax: any;


/**
 * Modal for displaying the modal for pipeline
 */
class PipelineModal {
    buildNumber : HTMLSpanElement;
    background: HTMLDivElement;
    modal: HTMLDivElement;
    textArea: HTMLTextAreaElement;
    closeButton: HTMLButtonElement;
    isOpen = false;

    constructor() {
        // pipeline is constructed via html page
        this.modal = document.getElementById("pipelineModal") as HTMLDivElement;
        if (this.modal == null) {
            throw new Error("Pipeline modal does not exist");
        }

        this.background = document.querySelector("#modalBackground") as HTMLDivElement;
        if (this.background == null) {
            throw new Error("Pipeline modal background does not exist");
        }
        this.background.onclick = (e: Event) => {
            this.closeModal();
        }
        this.buildNumber = this.modal.querySelector("#buildNumber") as HTMLSpanElement;
        if (this.buildNumber == null) {
            throw new Error("Pipeline dialog build number does not exist");
        }

        this.textArea = this.modal.querySelector("#pipelineModalTextArea") as HTMLTextAreaElement;
        if (this.textArea == null) {
            throw new Error("Pipeline dialog textarea does not exist");
        }
        this.closeButton = this.modal.querySelector("#confirmButton") as HTMLButtonElement;
        if (this.closeButton == null) {
            throw new Error("Pipeline dialog ok button does not exist");
        }
        this.closeButton.onclick = () => {
            this.closeModal();
        };
    }

    showModal() {
        this.modal.classList.add('visible');
        this.background.classList.add('visible');
        // makes sure we can't scroll the body when we are scrolling the text area
        document.documentElement.classList.add("is-clipped");
        this.isOpen = true;
    }

    closeModal() {
        this.modal.classList.remove('visible');
        this.background.classList.remove('visible');
        document.documentElement.classList.remove("is-clipped");
        this.isOpen = false;
    }

    addToElement(element: HTMLElement) {
        element.appendChild(this.modal);
        element.appendChild(this.background);
    }

    remove(element: HTMLElement) {
        element.removeChild(this.modal);
        element.removeChild(this.background);
    }
}


window.addEventListener('load', function() {
// create modal used for pipeline
const pipelineModal = new PipelineModal();
pipelineModal.addToElement(document.body);


enum StatusOfBuild {
    IN_PROGRESS = "IN_PROGRESS",
    ABORT = "ABORT",
    ERROR = "ERROR",
    SUCCESS = "SUCCESS"
};

// Part of jenkins api about builds (/jenkins/job/<name>/api/json)
interface JenkinsApiBuild {
    _class: string
    number: number
    url: string
}

// part of our custom api (/jenkins/job/<name>/<buildNumber>/api/json)
interface DslBuild {
    projectName: string
    buildNumber: number

    graphNodes: FinishedBuildJob[]
    duration: string
    status: string
    finished: boolean
    error?: string
}

interface FinishedBuildJob {
    projectName: string
    projectUri: string
    children: string[]

    // some builds might not have started, and will have a:
    // blank uri, -1, IN_PROGRESS status
    buildUri: string
    buildNumber: number
    buildStatus: string
    buildDuration : string
}

/**
 * Build status of one project that is using the DepBuilder DSL
 */
interface ProjectBuildStatus {
    jobBuildStatus: JobBuildInfo[]
    /**
     * Build status of the whole project (IN_PROGRESS, SUCCESS, ERROR, ABORT)
     */
    buildStatus: string
    /**
     * Duration for the whole project
     */
    duration: string
    /**
     * True if the project has finished building
     */
    finished: boolean
}

/**
 * Build status of one job in the graph
 */
interface JobBuildInfo {
    projectName: string
    buildStatus: string
    buildNumber: number
    duration : string
    /**
     * Relative build uri, that looks like: job/foo/32 (without the trailing slash)
     */
    buildUri: string
}

function createIcon(buildStatus: string) : string {
    switch(buildStatus) {
        case "ERROR":
        case "ABORT":
            return ICON_RESTART;
        case "IN_PROGRESS":
            return ICON_STOP;
        default:
            return ICON_PLAY;
    }
};

/**
 * Determines the tick of graph live update when the build is not yet finished. Small
 * tick might cause performance issues, large tick will cause the graph to lag behind
 * the rest of the user interface.
 */
const GRAPH_PROGRESS_UPDATE_MS = 5_000;

/**
 * Determines the tick of the background checker, that keeps checking for the latest
 * build. If the latest build has changed this checker will display a new graph in the
 * DOM and live update its progress.
 */
const LATEST_BUILD_CHECK_MS = 10_000;

/**
 * Maximum number of build data that we can fetch from the backend. If this number
 * has changed on the backend, it also has to change here (see numberOfGraphs in DslProject.java).
 */
const MAX_BUILDS_FETCHED = 20;

/**
 * The container for the entire graph (and not a container for one build node)
 */
class GraphDomNode {
    projectName: string;
    buildNumber : number;
    buildUri: string;
    pipeline?: string;
    currentState : string = "NONE";

    container : HTMLDivElement;
    progressLight : HTMLSpanElement;
    duration : HTMLSpanElement;
    cancelBuildButton : HTMLButtonElement;
    svg : SVGElement;

    constructor(projectName: string, buildNumber: number, buildUrl: string) {
        this.projectName = projectName;
        this.buildNumber = buildNumber;
        this.buildUri = buildUrl;

        // create the svg graph container node
        this.container = document.createElement("div");
        this.container.classList.add("graphContainer");
        this.container.innerHTML = `
        <div class="graphTitleContainer">
            <span id="progressLight" class="graphLight ${this.currentState}"></span>

            <h2>
            <a id="buildNumber" href="${this.buildNumber}/console">#${this.buildNumber}</a>
            <span id="projectName">${this.projectName}</span>
            <span id="duration" class="duration"></span>
            </h2>

            <span class="actionButtonsContainer">
                <span class="yui-button primary buttonContainer">
                    <button id="pipelineButton">${ICON_PIPELINE} Pipeline</button>
                </span><span class="yui-button danger buttonContainer">
                    <button id="abortButton">${ICON_CANCEL_X} Abort</button>
                </span>
            </span>
        </div>
        `

        this.progressLight = this.container.querySelector("#progressLight") as HTMLSpanElement;
        if (this.progressLight == null) {
            throw new Error("Progress light has changed the id selector");
        }

        this.duration = this.container.querySelector("#duration") as HTMLSpanElement;
        if (this.duration == null) {
            throw new Error("Duration label has changed the id selector");
        }

        let pipelineButton = this.container.querySelector("#pipelineButton") as HTMLButtonElement;
        if (pipelineButton == null) {
            throw new Error("Pipeline button selector did not find the button");
        }
        pipelineButton.onclick = () => this.showPipeline();

        this.cancelBuildButton = this.container.querySelector("#abortButton") as HTMLButtonElement;
        if (this.cancelBuildButton == null) {
            throw new Error("Cancel build button selector does not exist");
        }
        this.cancelBuildButton.onclick = () => this.abortBuildEvent();

        // update all container fields
        this.updateBuildStatus("NONE"); // update progress light to initial state

        // create a graph svg node
        const namespaceUri = "http://www.w3.org/2000/svg";
        this.svg = document.createElementNS(namespaceUri, "svg");
        this.svg.setAttribute('id', `buildGraph${this.buildNumber}`);
        this.svg.setAttribute('class', 'buildFlowGraph');
        let text = document.createElementNS(namespaceUri, "text");
        text.textContent = 'Fetching graph data';
        text.setAttribute('x', '20');
        text.setAttribute('y', '20');
        this.svg.appendChild(text);
        this.container.appendChild(this.svg);
    }

    private showPipeline() : void {
        let uri = this.buildUri + "api/json?tree=pipeline";
        if (!this.pipeline) {
            fetch(uri)
            .then(resp => {
                if (resp.ok) {
                    return resp.json();
                };
                throw resp;
            }).then(data => {
                let p : string = data['pipeline'];
                this.pipeline = p;
                this.showModal();
            }).catch(error => {
                console.error("Failed to fetch the pipeline");
                console.error(error);
            });
        } else {
            // we already fetched the pipeline, just show the modal
            this.showModal();
        }
    }

    // create modal for displaying the pipeline info...
    private showModal() {
        pipelineModal.buildNumber.innerText = "#" + this.buildNumber;
        if (this.pipeline) {
            pipelineModal.textArea.value = this.pipeline;
        } else {
            pipelineModal.textArea.value = "Pipeline does not exist";
        }
        pipelineModal.showModal();
    }

    // TODO: replace with enum
    updateBuildStatus(buildStatus: string) : void {
        this.currentState = buildStatus;
        this.progressLight.setAttribute("class", `graphLight ${buildStatus}`);

        switch(buildStatus) {
            case "IN_PROGRESS":
                this.cancelBuildButton.classList.remove("hidden")
                break;
            default:
                this.cancelBuildButton.classList.add("hidden");
        }
    }

    updateDuration(duration : string) {
        this.duration.innerText = `(${duration})`;
    }

    private abortBuildEvent() : void {
        if (this.currentState == "IN_PROGRESS") {
            // we should only abort builds that are in progress
            if (confirm(`Do you really want to abort #${this.buildNumber}?`)) {
                try {
                    // this is how the jenkins cancels the build and we have to use the same.
                    // If we try to create our own POST request via fetch api, we somehow
                    // miss something called jenkins-crumb and therefore get the 403 Forbidden
                    // response back. The same code is using /lib/resources/stopButton.jelly file
                    let abortUrl = this.buildUri + "stop";
                    new Ajax.Request(abortUrl);

                    // we hide the abort button immediately, since the
                    // server accepted the abort signal. If we wait for the
                    // next update event tick, abort button could be displayed
                    // for a few more seconds, which looks very confusing (as if nothing
                    // has happened). Manual hiding the button works better in that case.
                    this.cancelBuildButton.classList.add("hidden");
                } catch(error) {
                    console.error(error);
                }
            }
        }
    }
};

/**
 * Class used for displaying data in the graph one number of the project build
 */
class Graph {
    graphContainer : GraphDomNode;
    // TODO: is there a way to avoid null graph problem?
    graph? : any;
    buildData?: DslBuild;
    /**
     * Mapping projectName: buildStatus
     */
    buildStatus : Map<string, string>;
    buildNumber : number;
    buildUri : string;
    baseUri : string;

    SHOW_BUTTON = false; // determines if we display run buttons in the graph nodes
    // we need some more (url from the project)
    constructor(graphDomNode: GraphDomNode) {
        this.graphContainer = graphDomNode;
        this.buildNumber = this.graphContainer.buildNumber;
        this.buildUri = this.graphContainer.buildUri; // <domain/jenkins/job/<name>/<buildNumber>
        this.baseUri = this.getBaseUri(this.buildUri); // domain/jenkins
        this.buildStatus = new Map<string, string>();
    }

    private getBaseUri(uri : string) : string {
        let arr = uri.trim().split("/");
        arr.pop() // remove "" (uri ends with '/')
        arr.pop() // remove project number
        arr.pop() // remove project name
        arr.pop() // remove "job"
        return arr.join("/");
    }

    /**
     * Render the graph in the specified svg element
     */
    renderGraph() {
        // a new graph is created every time we create and render the graph
        // otherwise old edges stay in the graph and I don't know how to get rid of them
        const dagreGraph = new dagreD3.graphlib.Graph()
        .setGraph({})
        .setDefaultEdgeLabel(function() { return {}; });
        dagreGraph.graph().rankDir = "LR";

        if (!this.buildData) {
            // TODO: refactor, this is a bit problematic design. If the build data
            // does not exist we return an empty graph to avoid null problems.
            // Is there a way to avoid accessing the missing build data?
            return dagreGraph;
        }

        this.graphContainer.updateBuildStatus(this.buildData.status);
        this.graphContainer.updateDuration(this.buildData.duration);
        let nodes = this.buildData.graphNodes;
        for (let i = 0; i < nodes.length; i++) {
            let node = nodes[i];
            let projectName = node['projectName'];
            let projectUri = node['projectUri'];
            // when inspecting the build we are usually interested in inspecting the console
            // if the job is not yet build, the build uri is empty and should be filled later
            // on when we subscribe to build updates
            let buildUri = node['buildUri'] + "/console";
            let buildNumber = node['buildNumber'];
            let buildStatus = node['buildStatus'];
            let duration = node['buildDuration'];
            /*
             Edge case: it's possible that the build has finished, but the build number of
             every node is -1. This happens if the build data was not stored properly on
             the backend (build.xml file) and now it lacks build information or in the case
             deleted the build history (it could also be deleted automatically due to Jenkins build
             history rotation mechanism)
            */
            let buildNumberStr : string = buildNumber == -1 ? "" : "" + buildNumber;
            let buttonCode = this.SHOW_BUTTON ? `<span style="padding-right: 10px"></span>
                                            <button data-project="${projectName}" class="iconButton">${createIcon(buildStatus)}</button>`
                                         : "";

            // We are adding tooltip to the node, since the name could be fairly long and will be cut.
            // With tooltip at least the user can hover over the name and see the full project name.
            let htmlNode = `<div class="node-row">
                                <div class="projectNameContainer"><a target="_blank" class="hoverLink projectName" title="${projectName}" href="${projectUri}">${projectName}</a></div>
                                <span class="projectBuildSpacing"></span>
                                <a target="_blank" class="link buildLink hoverLink" href="${buildUri}">#${buildNumberStr}</a>
                                ${buttonCode}
                            </div>
                            <div class="node-row">
                                <p class="duration">${duration}</p>
                            </div>
                            `
            // class defines the css style class used for coloring the build node
            dagreGraph.setNode(projectName, {labelType:"html", class: buildStatus, label: htmlNode, rx: 4, ry: 4});
        }

        // create edges
        for (let i = 0; i < nodes.length; i++) {
            let node = nodes[i];
            let projectName = node['projectName'];
            let children = node['children'];
            for (let c = 0; c < children.length; c++) {
                let child = children[c];
                // for more options, see: https://github.com/d3/d3/blob/master/API.md#curves
                dagreGraph.setEdge(projectName, child, {curve: d3.curveBasis});
            }
        }

        // render
        const svg = d3.select(this.graphContainer.svg);
        svg.selectAll("*").remove(); // clear the graph of previous nodes
        let svgGroup = svg.append("g");

        let zoomFilter = (event: any) => {
            // see official documentation for more info: https://github.com/d3/d3-zoom#zoom_filter
            if (event.button) {
                // disabling any movement when right click is pressed
                // (context menu should be opened instead)
                return false;
            }

            if (event.type === 'dblclick') {
                // disabling double click for zoom in the graph
                return false;
            }

            if (event.type === 'wheel' && event.shiftKey) {
                // only allow zooming with scroll wheel when the shiftKey is held down.
                // This ensures when the user is scrolling down with a mouse we don't
                // hijack the down movement and start zooming in the graph
                return true;
            }

            if (!event.button && event.type !== 'wheel') {
                // allows panning
                return true;
            }

            // in any other case we don't allow moving the elements on the screen
            return false;
        }

        let zoom = d3.zoom().filter(() => {
            // ts ignore is forcing typescript to not check the following line.
            // The d3 is passing event through 'this' magic.
            //
            // @ts-ignore
            return zoomFilter(event);
        }).on("zoom", function() {
                svgGroup.attr("transform", d3.event.transform);
        });
        svg.call(zoom);

        let render = new dagreD3.render();
        render(svgGroup, dagreGraph);

        // setting attributes on svg have to happen after render
        const LEFT_BORDER_WIDTH = 2;
        svg.call(zoom.transform, d3.zoomIdentity.translate(LEFT_BORDER_WIDTH, 20));
        svg.attr("height", dagreGraph.graph().height + 60);


        // add play node button handlers
        if (this.SHOW_BUTTON) {
            svg.selectAll("g.node .iconButton").on("click", function(this:any) {
                // the only way to get the clicked button node is via d3 selector
                let button : any = d3.select(this);
                button.style("background-color", "red");
                let element : HTMLButtonElement | undefined = button.node();
                if (!element) {
                    // this should never happen
                    console.warn("Clicked button in the graph was not found via d3 selector");
                    return;
                }

                let project : string | undefined = element.dataset.project;
                if (!project) {
                    // this should never happen
                    console.error("Clicked button does not have a project dataset set");
                    return;
                }

                for (let node of nodes) {
                    if (node.projectName == project) {
                        // TODO: schedule a new build starting from this node...
                        console.error("TODO: Schedule a new build starting from project: " + project)
                        return;
                    }
                }
            });
        }
        this.graph = dagreGraph;
        return dagreGraph;
    }

    downloadAndRenderBuildData(): Promise<void> {
        return downloadBuildData(this.buildUri)
        .then(data => {
            if (data.ok) {
                return data.json();
            }
            throw data;
        })
        .then(dataJson => {
            // assign data to the graph node
            let data: DslBuild = dataJson['dslBuild'];
            this.buildData = data;

            // put the build status for every node in the graph in our internal map
            this.buildStatus.clear();
            let nodes = this.buildData.graphNodes;
            for (let node of nodes) {
                let name = node.projectName;
                let status = node.buildStatus;
                this.buildStatus.set(name, status);
            }

            this.renderGraph();
        })
        .catch(error => {
            let msg : string;
            if (error instanceof Response) {
                msg = `Error(${error.status}) while fetching build data at: ${error.url}`;
            } else {
                // we don't know what kind of error the server returned
                msg = `Error while fetching build data for ${this.buildUri}#${this.buildNumber}`;
            }
            this.graphContainer.svg.innerHTML = `<text x='20' y='20'>${msg}</text>`
        });
    }


    updateBuildStatus(projectBuildStatus: ProjectBuildStatus) : boolean {
        let jobsBuildStatus  = projectBuildStatus.jobBuildStatus;
        let isFinished = projectBuildStatus.finished;

        if (jobsBuildStatus == undefined) {
                // this should never happen
            throw new Error("'buildStatus' is undefined, the API on the backend has probably changed");
        }
        if (isFinished == undefined) {
            // this should never happen
            throw new Error("'finished' flag was not received from the api, the API on the backend has probably changed");
        }

        // update light in the graph dom based on the current status
        this.graphContainer.updateBuildStatus(projectBuildStatus.buildStatus);
        this.graphContainer.updateDuration(projectBuildStatus.duration);

        const graphIsMissing = this.graph?.nodes().length == 0;
        if (graphIsMissing)  {
            // This is an edge case that may happen when we are downloading pipeline
            // script via SCM. On the first createGraph method call, the pipeline
            // may not be downloaded yet and the graph structure is empty.
            // We have to populate the graph node by calling createGraph() method again.
            this.downloadAndRenderBuildData()
            .then(voidData => {
                this.updateGraphNodes(jobsBuildStatus);
            });
        } else {
            // normal workflow
            // graph is already present, just update the graph nodes
            this.updateGraphNodes(jobsBuildStatus);
        }

        return isFinished;
    }

    private updateGraphNodes(graphBuildStatus : JobBuildInfo[]) {
        for (let jobBuildStatus of graphBuildStatus) {
            let internalBuildStatus = this.buildStatus.get(jobBuildStatus.projectName);
            if (!internalBuildStatus) {
                // The internal build status for this project does not exist. This
                // should never happen since the map is populated when the build
                // structure is fetched for the first time (how the pipeline looks like)
                //
                // If the build name does not exist in the initial pipeline, then
                // something goofy has happened. We are early returning since such
                // graph won't be found in the d3 graph anyway.
                console.warn(`Build status ${jobBuildStatus.projectName} was not found in internal status map`);
                continue
            }

            let statusDidNotChange = internalBuildStatus == jobBuildStatus.buildStatus;
            if (statusDidNotChange) {
                if (jobBuildStatus.buildStatus != "IN_PROGRESS") {
                    // status didn't change and the status is not in progress (so it could
                    // either be done or haven't yet started. As such we don't have to update
                    // anything).
                    //
                    // This check is only here in case somebody creates a gigantic graph
                    // and we don't want our frontend performance to suffer with unnecessary
                    // node updates
                    continue;
                }

                // status did not change and build is in progress, fallthrough in order
                // to keep the duration, build numbers in the node updated
            } else {
                // build status has changed, update the internal map and fallthrough
                this.buildStatus.set(jobBuildStatus.projectName, jobBuildStatus.buildStatus);
            }

            // the build status has changed or the project build is IN_PROGRESS
            // as such we have to update the graph nodes
            let graphNode = this.graph?.node(jobBuildStatus.projectName);
            if (!graphNode) {
                // this should never happen, if it does we have a bug in the code.
                // either api returned wrong build nodes or we get the access to
                // the wrong graph
                console.warn("Build node of: " + jobBuildStatus.projectName + " is undefined");
                continue;
            }

            // Updates the status, duration, buildNumber of the build node in the graph
            try {
                // d3Node is <g> element coming from the d3, we don't have to
                // re-render the entire chart, but just change the class attribute
                let d3Node : SVGElement = graphNode.elem;
                d3Node.setAttribute("class", `node ${jobBuildStatus.buildStatus}`);
                let buildLinks : HTMLCollectionOf<Element> = d3Node.getElementsByClassName("buildLink");
                if (!buildLinks) {
                    // this should never happen
                    console.warn(`Build link ${jobBuildStatus.projectName}#${jobBuildStatus.buildNumber} was not found in the graph node`);
                } else {
                    for (let i = 0; i < buildLinks.length; i++) {
                        let element = buildLinks.item(i);
                        if (element instanceof HTMLAnchorElement) {
                            // @TODO: When we change the title of the node, the graph node
                            // does not enlarge/resize automatically. If we wanted to do that
                            // we would have to re-render the entire graph, which we shouldn't do
                            let anchor : HTMLAnchorElement = element;
                            anchor.innerText = jobBuildStatus.buildNumber == -1 ? "#" : "#" + jobBuildStatus.buildNumber;
                            anchor.href = `${this.baseUri}/${jobBuildStatus.buildUri}/console`
                        } else {
                            // this should never happen unless we changed the build node html
                            console.warn("Found element is not an anchor element");
                        }
                    }
                }

                // update duration field of the particular graph graph node
                let durationField : HTMLCollectionOf<Element> = d3Node.getElementsByClassName("duration");
                if (!durationField) {
                    // this should never happen
                    console.warn(`'duration' element is missing was not found in the graph node ${jobBuildStatus.projectName}#${jobBuildStatus.buildNumber}`);
                } else {
                    // it should be only one duration field in one d3Node
                    for (let i = 0; i < durationField.length; i++) {
                        let element = durationField.item(i);
                        if (element instanceof HTMLParagraphElement) {
                            element.innerText = jobBuildStatus.duration;
                        }
                    }
                }
            } catch (error) {
                // this should never happen, but we still catch it since one d3Node is not
                // typesafe and something might blow up in the future
                console.error(error);
            }
        }
    }

    fetchAndUpdateBuildStatus(): Promise<boolean> {
        const uri = this.buildUri + "api/json?tree=status";
        let promise : Promise<boolean> = fetch(uri).then(data => {
            if (data.ok) {
                return data.json();
            }
            throw data;
        })
        .then(dataJson => {
            let projectBuildStatus : ProjectBuildStatus = dataJson['status'];
            if (projectBuildStatus == undefined) {
                throw new Error(`Project build status ${this.buildUri} is undefined, the API on the backend has probably changed`);
            }
            let isFinished = this.updateBuildStatus(projectBuildStatus);
            return isFinished;
        })
        .catch(error => {
            if (error instanceof Response) {
                console.error(`${error.statusText} ${error.status}: failed to update builds status of ${this.buildUri}`);
            } else {
                console.error(`Failed to update build status of ${this.buildUri}, ${error}`);
            }

            // if we got an error, we can try again, but then again we could try
            // infinite times in case something is wrong, so we might as well
            // finish early
            return true;
        });

        return promise;
    }
}

function getWindowLocation(): string {
    let location = window.location.toString();
    let endsWithSlash = location[location.length - 1] === "/"
    if (endsWithSlash) {
        // trim the last slash
        return location.substr(0, location.length - 1);
    }
    return location;
}

/**
 * Continuously check for the last build of the project. If the new build was triggered
 * on Jenkins, a new graph of the new build will be added to the graph container
 */
function lastBuildChecker(projectName : string, lastBuild: JenkinsApiBuild, maxNumberOfGraphs: number) {
    const updateUrl = getWindowLocation() + "/api/json?tree=lastBuild[number,url]";
    const url = lastBuild.url;
    let arr = url.split("/");
    arr.pop(); // remove ""
    arr.pop(); // remove buildNumber
    let defaultUrl = arr.join("/");

    let previousBuildNumber = lastBuild.number;
    setInterval(() => {
        fetch(updateUrl)
        .then(data => {
            if (data.ok) {
                return data.json();
            }
            throw data;
        })
        .then(dataJson => {
            let lastBuildNumber : number | undefined = dataJson['lastBuild']?.number;
            if (!lastBuildNumber) {
                // last build number does not exist, this may be due to lastBuild being null (coming from
                // backend), which may happen due to user having old build history which is no longer
                // compatible with the new plugin version. In this case we simply early return.
                return;
            }

            // @FUTURE: this may not work correctly in case there are many builds of the same
            // project in parallel (many builds happen at once in less time than the tick of
            // this update interval). If that is the case this method will simply grab the latest
            // build and forget about the many builds in between.
            //
            // Initial implementation was increasing build numbers by 1, but that brought so
            // many problems and it was therefore scrapped (if the user deleted the build we
            // would render nonexisting build/error which is completely bad user experience).
            // A better solution for edge case: lastBuildNumber - previousBuildNumber > 1
            // would be to simply create a request for the latest lastBuildNumber-previousBuildNumber
            // and just render if any of those should be rendered.
            let buildHasChanged = lastBuildNumber > previousBuildNumber;
            if (buildHasChanged) {
                let currentBuildUrl = defaultUrl + "/" + lastBuildNumber + "/";
                let build : JenkinsApiBuild = {
                    _class: "",
                    url: currentBuildUrl,
                    number: lastBuildNumber
                };
                downloadAndRenderBuild(projectName, build, POSITION_SVG.PREPEND, maxNumberOfGraphs);
                previousBuildNumber = lastBuildNumber;
            }
        })
        .catch(error => {
            console.error(error);
        })
    }, LATEST_BUILD_CHECK_MS);
}

enum POSITION_SVG {
    APPEND,
    PREPEND
};

/**
 * Download build data for a specific build
 * @param buildUrl (http://baseJenkinsUri/<project>/<number>)
 */
function downloadBuildData(buildUrl: string): Promise<Response> {
    const uri = buildUrl + "api/json?tree=dslBuild"
    return fetch(uri);
}

/**
 * Download build data for a specific build and render a dependency graph
 */
function downloadAndRenderBuild(projectName: string, build : JenkinsApiBuild, typeOfAdd: POSITION_SVG = POSITION_SVG.APPEND, maxNumberOfGraphs: number) {
    const svgContainer : HTMLElement | null = document.getElementById("svgContainer");
    if (!svgContainer) {
        console.error("SVG container could not be found, this is a bug");
        return;
    }

    let graphDomNode = new GraphDomNode(projectName, build.number, build.url);

    // on the first page render we are rendering builds from highest number to lowest
    // and for this reason we have to append them to the container (highest build
    // number should appear at the top, because it's the first element returned from
    // the json call)
    //
    // When we are updating the existing graph (via background checker), we would like
    // to prepend new builds, so they appear at the top of the graph.
    if (typeOfAdd == POSITION_SVG.APPEND) {
        svgContainer.appendChild(graphDomNode.container);
    } else {
        svgContainer.prepend(graphDomNode.container);
        // if we are pre-pending that means we are adding a new data (it's not the
        // first page render, so we have to remove the old nodes from the DOM to not
        // spend gigabytes of memory for no good reason if someone keeps the tab open)
        // @CLEANUP: this is a hacky workaround, maybe find a nicer solution
        if (svgContainer.childNodes.length > maxNumberOfGraphs) {
            // If we were dynamically changing maxNumberOfGraphs, this wouldn't work and
            //  we would have to calculate the amount of child node to remove from the back
            svgContainer.lastChild?.remove();
        }
    }

    let graph = new Graph(graphDomNode);
    graph.downloadAndRenderBuildData()
    .then(voidData => {
        let isFinished = graph.buildData? graph.buildData.finished : false;
        if (!isFinished) {
            // since the build is still not finished, we schedule the periodic update that
            // will keep firing and updating graph nodes until the build is finished
            let backgroundUpdater = -1;
            backgroundUpdater = setInterval(() => {
                let finished = graph.fetchAndUpdateBuildStatus();
                finished.then(taskIsDone => {
                    if (taskIsDone) {
                        clearInterval(backgroundUpdater);
                    }
                }).catch(error => {
                    console.error(error);
                    clearInterval(backgroundUpdater);
                })
            }, GRAPH_PROGRESS_UPDATE_MS);
        }
    })
}

// fetch data about builds on the first load
function fetchAndRenderAllBuilds() {
    const buildsUri = getWindowLocation() + `/api/json?tree=builds[number,url]{0,${MAX_BUILDS_FETCHED}},name,numberOfGraphs`;
    fetch(buildsUri)
    .then(data => {
        if (data.ok) {
            return data.json();
        }
        throw data;
    })
    .then(jsonData => {
        const projectName = jsonData['name']? jsonData['name'] : "Unknown project";
        const maxNumberOfGraphs: number = jsonData['numberOfGraphs']? jsonData['numberOfGraphs'] : 5;
        const builds: JenkinsApiBuild[] = jsonData['builds'];

        if (builds == undefined || builds.length == 0) {
            // We could display the message that no builds exists (svg element)
            // but then we would have to deal with that message and figuring out how to resolve that message
            //
            // We are relying on the method implementation which is currently expecting url in such format
            // If we ever use this method on a non project root page, we will have to get rid of this string
            // manipulation
            let noBuild : JenkinsApiBuild = {_class: "", number: 0, url: `${getWindowLocation()}/0/`};
            lastBuildChecker(projectName, noBuild, maxNumberOfGraphs);
            return;
        }

        // builds for the project exists, render them in their svg elements, but at most 5 of them
        let maxNumberOfBuilds = Math.min(maxNumberOfGraphs, builds.length);
        for (let i = 0; i < maxNumberOfBuilds; i++) {
            let build = builds[i];
            downloadAndRenderBuild(projectName, build, POSITION_SVG.APPEND, maxNumberOfGraphs);
        }

        // keep checking whether the user scheduled a new build and if so, the new
        // graph should be rendered at the top of the svg container
        let lastBuild = builds[0];
        lastBuildChecker(projectName, lastBuild, maxNumberOfGraphs);
    })
    .catch(error => {
        if (error instanceof Response) {
            console.error(`Error(${error.status}) while fetching graph data for: ${error.url}`)
        } else {
            console.error(error);
        }
    });
}

// fetch builds on the page load
fetchAndRenderAllBuilds();

}); // end of load
