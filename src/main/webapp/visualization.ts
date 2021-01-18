'use strict';
// Examples: https://github.com/dagrejs/dagre-d3/wiki
// Graphlib API: https://github.com/dagrejs/graphlib/wiki/API-Reference

// BuildNode presents one job on configuration page
interface BuildNode {
    projectName: string
    projectUri: string
    children: string[]
}

window.addEventListener('load', function() {

// create and render build job graph in the UI
function createGraph(nodes: BuildNode[], cycle: string[], renderElement: string) {
    // a new graph is created every time we create and render the graph
    // otherwise old edges stay in the graph and I don't know how to get rid of them
    const g = new dagreD3.graphlib.Graph()
      .setGraph({})
      .setDefaultEdgeLabel(function() { return {}; });
    g.graph().rankDir = "LR";

    // create nodes
    for (let i = 0; i < nodes.length; i++) {
        let node = nodes[i];
        let projectName = node['projectName'];
        let projectUri = node['projectUri'];

        let htmlNode = "";
        if (projectUri == undefined) {
            htmlNode = `<p>${projectName}</p>`
        } else {
            htmlNode = `<a class="hoverLink" target="_blank" href=${projectUri}>${projectName}</a>`
        }
        // beside label we can add class: 'my-class' to style a certain node
        g.setNode(projectName, {labelType:"html", label: htmlNode, rx: 4, ry: 4});
    }

    // create edges
    for (let i = 0; i < nodes.length; i++) {
        let node = nodes[i];
        let projectName = node['projectName'];
        let children = node['children'];
        for (let c = 0; c < children.length; c++) {
            let childName = children[c];
            // for more options, see: https://github.com/d3/d3/blob/master/API.md#curves
            g.setEdge(projectName, childName, {curve: d3.curveBasis});
        }
    }

    // if there are cycles in the graph, mark them as such
    let hasCycles = cycle.length > 0;
    if (hasCycles) {
        for (let i = 0; i <  cycle.length; i++) {
            let jobId = cycle[i];
            let node = g.node(jobId);
            if (node == undefined) {
                // this should never happen, if it does it's probably a bug
                // on the backend
                console.warn("Marking cycle for job node: " + jobId + " is undefined");
                continue;
            }
            node.class = "errorNode";
        }
    }

    // render
    const svg = d3.select(renderElement);
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
    let zoom = d3.zoom().on("zoom", function() {
            svgGroup.attr("transform", d3.event.transform);
    });
    zoom.filter(() => {
        // @ts-ignore
        return zoomFilter(event);
    });
    svg.call(zoom);

    let render = new dagreD3.render();
    render(svgGroup, g);

    svg.call(zoom.transform, d3.zoomIdentity.translate(10, 20));
    svg.attr("height", g.graph().height + 60);
}

function createApiLocation() {
    // drop last "/configure" and replace it with /api/json
    let url = window.location;
    let arr = url.pathname.split("/");
    arr.pop();
    let newURL = url.origin + arr.join("/") + "/api/json?tree=buildConfiguration";
    return newURL
}

function displayGraph() {
    const renderElement = "#svg-canvas";
    const apiURL = createApiLocation();
    fetch(apiURL)
    .then(data => {
        return data.json();
    })
    .then(jsonData => {
        // this field contains current build configuration
        let data = jsonData['buildConfiguration'];
        let error = data['error'];
        if (error != undefined) {
            throw new Error(error);
        }

        // everything was fine, render the graph with nodes
        // build: {id: "jobName", children: ["job1", "job2"], url: https://xxxx/job/project/}
        // cycle: ["job1", "job2", "job3"]
        let buildJobs: BuildNode[] = data["build"];
        let cycle: string[] = data["cycle"];
        createGraph(buildJobs, cycle, renderElement);
    })
    .catch(error => {
        if (error instanceof Error) {
            // display error node as a single node in the chart
            let buildJobs = [{"projectName": "Error:\n" + error.message, "children": [], projectUri: ""}];
            let cycle : string[] = [];
            createGraph(buildJobs, cycle, renderElement);
        } else {
            console.error(error);
        }
    });
}

function handleInputScriptChange(newValue: string, pipelineSection: HTMLElement, scmSection: HTMLElement) {
    switch (newValue) {
        case "SCRIPT":
            pipelineSection.classList.remove("hidden");
            scmSection.classList.add("hidden");

            // WORKAROUND: when we render SCM first and then we switch to SCRIPT
            // the graph is not rendered completely (job names are not visible)
            // This ensures that graph is always visible.
            displayGraph();
        break;
        case "SCM":
            pipelineSection.classList.add("hidden");
            scmSection.classList.remove("hidden");
        break;
        default:
            console.error("Invalid input script option: " + newValue);
    }
}

let inputTypeSelector : HTMLSelectElement | null = document.getElementById("scriptInputTypeSelector") as HTMLSelectElement;
let pipelineSection : HTMLElement | null = document.getElementById("pipelineSection");
let scmSection : HTMLElement | null = document.getElementById("scmSection");
if (inputTypeSelector == null) {
    throw Error("Input type selector (select HTML element) no longer exists. The DOM selector has probably changed");
}

if (pipelineSection == null) {
    throw Error("Pipeline section no longer exists. The DOM selector has probably changed");
}

if (scmSection == null) {
    throw Error("SCM section no longer exists. The DOM selector has probably changed");
}

// graph is rendered when we handle the switch for the first time
handleInputScriptChange(inputTypeSelector.selectedOptions[0].value, pipelineSection, scmSection);
inputTypeSelector.addEventListener("change", (e) => {
    if (inputTypeSelector == null) {
        throw new Error("Input type selector is null, this should never happen");
    }
    if (pipelineSection == null) {
        throw new Error("Pipeline section is null, this should never happen");
    }
    if (scmSection == null) {
        throw new Error("SCM section is null, this should never happen");
    }

    let selectedOption : string = inputTypeSelector.selectedOptions[0].value;
    handleInputScriptChange(selectedOption, pipelineSection, scmSection);
});


// whenever the dsl change event triggers, rerender the graph
let pipelineArea : any | null = document.getElementById("pipelineArea");
if (pipelineArea == null) {
    console.error("Pipeline visualization DOM element does not exist. The DOM selector has probably changed.");
} else {
    pipelineArea.onDslChange = function() {
        displayGraph();
    }
}

});
