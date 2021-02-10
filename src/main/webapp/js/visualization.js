'use strict';
window.addEventListener('load', function () {
    function createGraph(nodes, cycle, renderElement) {
        var g = new dagreD3.graphlib.Graph()
            .setGraph({})
            .setDefaultEdgeLabel(function () { return {}; });
        g.graph().rankDir = "LR";
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var projectName = node['projectName'];
            var projectUri = node['projectUri'];
            var htmlNode = "";
            if (projectUri == undefined) {
                htmlNode = "<p>" + projectName + "</p>";
            }
            else {
                htmlNode = "<a class=\"hoverLink\" target=\"_blank\" href=" + projectUri + ">" + projectName + "</a>";
            }
            g.setNode(projectName, { labelType: "html", label: htmlNode, rx: 4, ry: 4 });
        }
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var projectName = node['projectName'];
            var children = node['children'];
            for (var c = 0; c < children.length; c++) {
                var childName = children[c];
                g.setEdge(projectName, childName, { curve: d3.curveBasis });
            }
        }
        var hasCycles = cycle.length > 0;
        if (hasCycles) {
            for (var i = 0; i < cycle.length; i++) {
                var jobId = cycle[i];
                var node = g.node(jobId);
                if (node == undefined) {
                    console.warn("Marking cycle for job node: " + jobId + " is undefined");
                    continue;
                }
                node.class = "errorNode";
            }
        }
        var svg = d3.select(renderElement);
        svg.selectAll("*").remove();
        var svgGroup = svg.append("g");
        var zoomFilter = function (event) {
            if (event.button) {
                return false;
            }
            if (event.type === 'dblclick') {
                return false;
            }
            if (event.type === 'wheel' && event.shiftKey) {
                return true;
            }
            if (!event.button && event.type !== 'wheel') {
                return true;
            }
            return false;
        };
        var zoom = d3.zoom().on("zoom", function () {
            svgGroup.attr("transform", d3.event.transform);
        });
        zoom.filter(function () {
            return zoomFilter(event);
        });
        svg.call(zoom);
        var render = new dagreD3.render();
        render(svgGroup, g);
        svg.call(zoom.transform, d3.zoomIdentity.translate(10, 20));
        svg.attr("height", g.graph().height + 60);
    }
    function createApiLocation() {
        var url = window.location;
        var arr = url.pathname.split("/");
        arr.pop();
        var newURL = url.origin + arr.join("/") + "/api/json?tree=buildConfiguration";
        return newURL;
    }
    function displayGraph() {
        var renderElement = "#svg-canvas";
        var apiURL = createApiLocation();
        fetch(apiURL)
            .then(function (data) {
            return data.json();
        })
            .then(function (jsonData) {
            var data = jsonData['buildConfiguration'];
            var error = data['error'];
            if (error != undefined) {
                throw new Error(error);
            }
            var buildJobs = data["build"];
            var cycle = data["cycle"];
            createGraph(buildJobs, cycle, renderElement);
        })
            .catch(function (error) {
            if (error instanceof Error) {
                var buildJobs = [{ "projectName": "Error:\n" + error.message, "children": [], projectUri: "" }];
                var cycle = [];
                createGraph(buildJobs, cycle, renderElement);
            }
            else {
                console.error(error);
            }
        });
    }
    function handleInputScriptChange(newValue, pipelineSection, scmSection) {
        switch (newValue) {
            case "SCRIPT":
                pipelineSection.classList.remove("hidden");
                scmSection.classList.add("hidden");
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
    var inputTypeSelector = document.getElementById("scriptInputTypeSelector");
    var pipelineSection = document.getElementById("pipelineSection");
    var scmSection = document.getElementById("scmSection");
    if (inputTypeSelector == null) {
        throw Error("Input type selector (select HTML element) no longer exists. The DOM selector has probably changed");
    }
    if (pipelineSection == null) {
        throw Error("Pipeline section no longer exists. The DOM selector has probably changed");
    }
    if (scmSection == null) {
        throw Error("SCM section no longer exists. The DOM selector has probably changed");
    }
    handleInputScriptChange(inputTypeSelector.selectedOptions[0].value, pipelineSection, scmSection);
    inputTypeSelector.addEventListener("change", function (e) {
        if (inputTypeSelector == null) {
            throw new Error("Input type selector is null, this should never happen");
        }
        if (pipelineSection == null) {
            throw new Error("Pipeline section is null, this should never happen");
        }
        if (scmSection == null) {
            throw new Error("SCM section is null, this should never happen");
        }
        var selectedOption = inputTypeSelector.selectedOptions[0].value;
        handleInputScriptChange(selectedOption, pipelineSection, scmSection);
    });
    var pipelineArea = document.getElementById("pipelineArea");
    if (pipelineArea == null) {
        console.error("Pipeline visualization DOM element does not exist. The DOM selector has probably changed.");
    }
    else {
        pipelineArea.onDslChange = function () {
            displayGraph();
        };
    }
});
//# sourceMappingURL=visualization.js.map