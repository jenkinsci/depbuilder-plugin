"use strict";
var ICON_STOP = "<svg class=\"icon STOP\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 448 512\"><path class=\"iconColor\" d=\"M400 32H48C21.5 32 0 53.5 0 80v352c0 26.5 21.5 48 48 48h352c26.5 0 48-21.5 48-48V80c0-26.5-21.5-48-48-48z\"></path></svg>";
var ICON_PLAY = "<svg class=\"icon PLAY\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 448 512\"><path class=\"iconColor\" d=\"M424.4 214.7L72.4 6.6C43.8-10.3 0 6.1 0 47.9V464c0 37.5 40.7 60.1 72.4 41.3l352-208c31.4-18.5 31.5-64.1 0-82.6z\"></path></svg>";
var ICON_RESTART = "<svg class=\"icon RESTART\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><path class=\"iconColor\" d=\"M256.455 8c66.269.119 126.437 26.233 170.859 68.685l35.715-35.715C478.149 25.851 504 36.559 504 57.941V192c0 13.255-10.745 24-24 24H345.941c-21.382 0-32.09-25.851-16.971-40.971l41.75-41.75c-30.864-28.899-70.801-44.907-113.23-45.273-92.398-.798-170.283 73.977-169.484 169.442C88.764 348.009 162.184 424 256 424c41.127 0 79.997-14.678 110.629-41.556 4.743-4.161 11.906-3.908 16.368.553l39.662 39.662c4.872 4.872 4.631 12.815-.482 17.433C378.202 479.813 319.926 504 256 504 119.034 504 8.001 392.967 8 256.002 7.999 119.193 119.646 7.755 256.455 8z\"></path></svg>";
var ICON_PIPELINE = "<svg class=\"icon PIPELINE\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><path class=\"iconColor\" d=\"M504.971 359.029c9.373 9.373 9.373 24.569 0 33.941l-80 79.984c-15.01 15.01-40.971 4.49-40.971-16.971V416h-58.785a12.004 12.004 0 0 1-8.773-3.812l-70.556-75.596 53.333-57.143L352 336h32v-39.981c0-21.438 25.943-31.998 40.971-16.971l80 79.981zM12 176h84l52.781 56.551 53.333-57.143-70.556-75.596A11.999 11.999 0 0 0 122.785 96H12c-6.627 0-12 5.373-12 12v56c0 6.627 5.373 12 12 12zm372 0v39.984c0 21.46 25.961 31.98 40.971 16.971l80-79.984c9.373-9.373 9.373-24.569 0-33.941l-80-79.981C409.943 24.021 384 34.582 384 56.019V96h-58.785a12.004 12.004 0 0 0-8.773 3.812L96 336H12c-6.627 0-12 5.373-12 12v56c0 6.627 5.373 12 12 12h110.785c3.326 0 6.503-1.381 8.773-3.812L352 176h32z\"></path></svg>";
var ICON_BAN = "<svg class=\"icon BAN\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\"><path class=\"iconColor\" d=\"M256 8C119.034 8 8 119.033 8 256s111.034 248 248 248 248-111.034 248-248S392.967 8 256 8zm130.108 117.892c65.448 65.448 70 165.481 20.677 235.637L150.47 105.216c70.204-49.356 170.226-44.735 235.638 20.676zM125.892 386.108c-65.448-65.448-70-165.481-20.677-235.637L361.53 406.784c-70.203 49.356-170.226 44.736-235.638-20.676z\"></path></svg>";
var ICON_CANCEL_X = "<svg class=\"icon CANCEL_X\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 352 512\"><path class=\"iconColor\" d=\"M242.72 256l100.07-100.07c12.28-12.28 12.28-32.19 0-44.48l-22.24-22.24c-12.28-12.28-32.19-12.28-44.48 0L176 189.28 75.93 89.21c-12.28-12.28-32.19-12.28-44.48 0L9.21 111.45c-12.28 12.28-12.28 32.19 0 44.48L109.28 256 9.21 356.07c-12.28 12.28-12.28 32.19 0 44.48l22.24 22.24c12.28 12.28 32.2 12.28 44.48 0L176 322.72l100.07 100.07c12.28 12.28 32.2 12.28 44.48 0l22.24-22.24c12.28-12.28 12.28-32.19 0-44.48L242.72 256z\"></path></svg>";
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
var PipelineModal = (function () {
    function PipelineModal() {
        var _this = this;
        this.isOpen = false;
        this.modal = document.getElementById("pipelineModal");
        if (this.modal == null) {
            throw new Error("Pipeline modal does not exist");
        }
        this.background = document.querySelector("#modalBackground");
        if (this.background == null) {
            throw new Error("Pipeline modal background does not exist");
        }
        this.background.onclick = function (e) {
            _this.closeModal();
        };
        this.buildNumber = this.modal.querySelector("#buildNumber");
        if (this.buildNumber == null) {
            throw new Error("Pipeline dialog build number does not exist");
        }
        this.textArea = this.modal.querySelector("#pipelineModalTextArea");
        if (this.textArea == null) {
            throw new Error("Pipeline dialog textarea does not exist");
        }
        this.closeButton = this.modal.querySelector("#confirmButton");
        if (this.closeButton == null) {
            throw new Error("Pipeline dialog ok button does not exist");
        }
        this.closeButton.onclick = function () {
            _this.closeModal();
        };
    }
    PipelineModal.prototype.showModal = function () {
        this.modal.classList.add('visible');
        this.background.classList.add('visible');
        document.documentElement.classList.add("is-clipped");
        this.isOpen = true;
    };
    PipelineModal.prototype.closeModal = function () {
        this.modal.classList.remove('visible');
        this.background.classList.remove('visible');
        document.documentElement.classList.remove("is-clipped");
        this.isOpen = false;
    };
    PipelineModal.prototype.addToElement = function (element) {
        element.appendChild(this.modal);
        element.appendChild(this.background);
    };
    PipelineModal.prototype.remove = function (element) {
        element.removeChild(this.modal);
        element.removeChild(this.background);
    };
    return PipelineModal;
}());
window.addEventListener('load', function () {
    var pipelineModal = new PipelineModal();
    pipelineModal.addToElement(document.body);
    var BuildStatus;
    (function (BuildStatus) {
        BuildStatus["ERROR"] = "ERROR";
        BuildStatus["ABORT"] = "ABORT";
        BuildStatus["SUCCESS"] = "SUCCESS";
        BuildStatus["IN_PROGRESS"] = "IN_PROGRESS";
        BuildStatus["NO_BUILD"] = "NO_BUILD";
        BuildStatus["NONE"] = "NONE";
    })(BuildStatus || (BuildStatus = {}));
    ;
    function createIcon(buildStatus) {
        switch (buildStatus) {
            case "IN_PROGRESS":
                return ICON_STOP;
            default:
                return ICON_PLAY;
        }
    }
    ;
    var GRAPH_PROGRESS_UPDATE_MS = 5000;
    var LATEST_BUILD_CHECK_MS = 10000;
    var MAX_BUILDS_FETCHED = 20;
    var GraphTitleBar = (function () {
        function GraphTitleBar(projectName, buildNumber, buildUrl) {
            var _this = this;
            this.currentState = "NONE";
            this.projectName = projectName;
            this.buildNumber = buildNumber;
            this.buildUri = buildUrl;
            this.container = document.createElement("div");
            this.container.classList.add("graphContainer");
            this.container.innerHTML = "\n        <div class=\"graphTitleContainer\">\n            <span id=\"progressLight\" class=\"graphLight " + this.currentState + "\"></span>\n\n            <h2>\n            <a id=\"buildNumber\" href=\"" + this.buildNumber + "/console\">#" + this.buildNumber + "</a>\n            <span id=\"projectName\">" + this.projectName + "</span>\n            <span id=\"duration\" class=\"duration\"></span>\n            </h2>\n\n            <span class=\"actionButtonsContainer\">\n                <span class=\"yui-button primary buttonContainer\">\n                    <button id=\"pipelineButton\">" + ICON_PIPELINE + " Pipeline</button>\n                </span><span class=\"yui-button danger buttonContainer\">\n                    <button id=\"abortButton\">" + ICON_CANCEL_X + " Abort</button>\n                </span>\n            </span>\n        </div>\n        ";
            this.progressLight = this.container.querySelector("#progressLight");
            if (this.progressLight == null) {
                throw new Error("Progress light has changed the id selector");
            }
            this.duration = this.container.querySelector("#duration");
            if (this.duration == null) {
                throw new Error("Duration label has changed the id selector");
            }
            var pipelineButton = this.container.querySelector("#pipelineButton");
            if (pipelineButton == null) {
                throw new Error("Pipeline button selector did not find the button");
            }
            pipelineButton.onclick = function () { return _this.showPipeline(); };
            this.cancelBuildButton = this.container.querySelector("#abortButton");
            if (this.cancelBuildButton == null) {
                throw new Error("Cancel build button selector does not exist");
            }
            this.cancelBuildButton.onclick = function () { return _this.abortBuildEvent(); };
            this.updateBuildStatus(BuildStatus.NONE);
            var namespaceUri = "http://www.w3.org/2000/svg";
            this.svg = document.createElementNS(namespaceUri, "svg");
            this.svg.setAttribute('id', "buildGraph" + this.buildNumber);
            this.svg.setAttribute('class', 'buildFlowGraph');
            var text = document.createElementNS(namespaceUri, "text");
            text.textContent = 'Fetching graph data';
            text.setAttribute('x', '20');
            text.setAttribute('y', '20');
            this.svg.appendChild(text);
            this.container.appendChild(this.svg);
        }
        GraphTitleBar.prototype.showPipeline = function () {
            var _this = this;
            var uri = this.buildUri + "api/json?tree=pipeline";
            if (!this.pipeline) {
                fetch(uri)
                    .then(function (resp) {
                    if (resp.ok) {
                        return resp.json();
                    }
                    ;
                    throw resp;
                }).then(function (data) {
                    var p = data['pipeline'];
                    _this.pipeline = p;
                    _this.showModal();
                }).catch(function (error) {
                    console.error("Failed to fetch the pipeline");
                    console.error(error);
                });
            }
            else {
                this.showModal();
            }
        };
        GraphTitleBar.prototype.showModal = function () {
            pipelineModal.buildNumber.innerText = "#" + this.buildNumber;
            if (this.pipeline) {
                pipelineModal.textArea.value = this.pipeline;
            }
            else {
                pipelineModal.textArea.value = "Pipeline does not exist";
            }
            pipelineModal.showModal();
        };
        GraphTitleBar.prototype.updateBuildStatus = function (buildStatus) {
            this.currentState = buildStatus;
            this.progressLight.setAttribute("class", "graphLight " + buildStatus);
            switch (buildStatus) {
                case "IN_PROGRESS":
                    this.cancelBuildButton.classList.remove("displayNone");
                    break;
                default:
                    this.cancelBuildButton.classList.add("displayNone");
            }
        };
        GraphTitleBar.prototype.updateTotalBuildDuration = function (duration) {
            this.duration.innerText = "(" + duration + ")";
        };
        GraphTitleBar.prototype.abortBuildEvent = function () {
            if (this.currentState == "IN_PROGRESS") {
                if (confirm("Do you really want to abort #" + this.buildNumber + "?")) {
                    try {
                        var abortUrl = this.buildUri + "stop";
                        new Ajax.Request(abortUrl);
                        this.cancelBuildButton.classList.add("displayNone");
                    }
                    catch (error) {
                        console.error(error);
                    }
                }
            }
        };
        return GraphTitleBar;
    }());
    ;
    var Graph = (function () {
        function Graph(graphDomNode) {
            this.graphTitleBar = graphDomNode;
            this.buildNumber = this.graphTitleBar.buildNumber;
            this.buildUri = this.graphTitleBar.buildUri;
            this.baseUri = this.getBaseUri(this.buildUri);
            this.buildStatus = new Map();
        }
        Graph.prototype.getBaseUri = function (uri) {
            var arr = uri.trim().split("/");
            arr.pop();
            arr.pop();
            arr.pop();
            arr.pop();
            return arr.join("/");
        };
        Graph.prototype.renderGraph = function () {
            var dagreGraph = new dagreD3.graphlib.Graph()
                .setGraph({})
                .setDefaultEdgeLabel(function () { return {}; });
            dagreGraph.graph().rankDir = "LR";
            if (!this.buildData) {
                return dagreGraph;
            }
            this.graphTitleBar.updateBuildStatus(this.buildData.status);
            this.graphTitleBar.updateTotalBuildDuration(this.buildData.duration);
            var nodes = this.buildData.graphNodes;
            for (var i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                var projectName = node['projectName'];
                var displayName = node['displayName'];
                var projectUri = node['projectUri'];
                var buildUri = node['buildUri'] + "/console";
                var buildNumber = node['buildNumber'];
                var buildStatus = node['buildStatus'];
                var duration = node['buildDuration'];
                var buildNumberStr = buildNumber == -1 ? "" : "" + buildNumber;
                var htmlNode = "<div class=\"node-row\">\n                                <div class=\"projectNameContainer\"><a target=\"_blank\" class=\"hoverLink projectName\" title=" + projectName + " href=\"" + projectUri + "\">" + displayName + "</a></div>\n                                <span class=\"projectBuildSpacing\"></span>\n                                <a target=\"_blank\" class=\"link buildLink hoverLink\" href=\"" + buildUri + "\">#" + buildNumberStr + "</a>\n                            </div>\n                            <div class=\"node-row\">\n                                <p class=\"duration\">" + duration + "</p>\n                                <span class=\"projectBuildSpacing\"></span>\n                                <button data-project=\"" + projectName + "\" data-status=\"" + buildStatus + "\"\n                                    class=\"iconButton\">" + createIcon(buildStatus) + "\n                                </button>\n                            </div>\n                            ";
                var paddingSide = 7;
                var paddingTop = 5;
                var radius = 4;
                dagreGraph.setNode(projectName, { labelType: "html", class: buildStatus, label: htmlNode,
                    rx: radius,
                    ry: radius,
                    paddingLeft: paddingSide,
                    paddingRight: paddingSide,
                    paddingTop: paddingTop,
                    paddingBottom: paddingTop
                });
            }
            for (var i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                var projectName = node['projectName'];
                var children = node['children'];
                for (var c = 0; c < children.length; c++) {
                    var child = children[c];
                    dagreGraph.setEdge(projectName, child, { curve: d3.curveBasis });
                }
            }
            var svg = d3.select(this.graphTitleBar.svg);
            svg.selectAll("*").remove();
            var svgGroup = svg.append("g");
            var zoom = d3.zoom().filter(function () {
                return zoomFilter(event);
            }).on("zoom", function () {
                svgGroup.attr("transform", d3.event.transform);
            });
            svg.call(zoom);
            var render = new dagreD3.render();
            render(svgGroup, dagreGraph);
            var LEFT_BORDER_WIDTH = 2;
            svg.call(zoom.transform, d3.zoomIdentity.translate(LEFT_BORDER_WIDTH, 20));
            svg.attr("height", dagreGraph.graph().height + 60);
            this.graph = dagreGraph;
            var outerRef = this;
            svg.selectAll("g.node .iconButton").on("click", function () {
                var buttonElement = d3.select(this);
                var button = buttonElement.node();
                if (!button) {
                    console.warn("Clicked button in the graph was not found via d3 selector");
                    return;
                }
                var status = button.dataset.status;
                if (!status) {
                    console.error("Clicked action button does not have a status dataset set");
                    return;
                }
                var buildStatus = status;
                outerRef.executeIconButtonAction(button, buildStatus);
            });
            return dagreGraph;
        };
        Graph.prototype.executeIconButtonAction = function (button, buildStatus) {
            var project = button.dataset.project;
            if (!project) {
                console.error("Clicked button does not have a project dataset set");
                return;
            }
            button.classList.add("hidden");
            setTimeout(function () {
                button.classList.remove("hidden");
            }, 10000);
            switch (buildStatus) {
                case "IN_PROGRESS":
                    var abortUri = this.buildUri + "stop";
                    new Ajax.Request(abortUri, {
                        contentType: "application/json",
                        onFailure: function (response) {
                            alert("Failed to stop the build.\nServer response: " + response.status);
                        }
                    });
                    break;
                default:
                    var params = new URLSearchParams({
                        job: project,
                        delay: "0sec"
                    });
                    var url = this.buildUri + "startPartialBuild?" + params.toString();
                    new Ajax.Request(url, {
                        dataType: "json",
                        contentType: "application/json",
                        onFailure: function (response) {
                            alert("Failed to trigger build of: " + project + ".\nServer response: " + response.status);
                        }
                    });
            }
        };
        Graph.prototype.downloadAndRenderGraph = function () {
            var _this = this;
            return downloadBuildData(this.buildUri)
                .then(function (data) {
                if (data.ok) {
                    return data.json();
                }
                throw data;
            })
                .then(function (dataJson) {
                var data = dataJson['dslBuild'];
                _this.buildData = data;
                _this.buildStatus.clear();
                var nodes = _this.buildData.graphNodes;
                for (var _i = 0, nodes_1 = nodes; _i < nodes_1.length; _i++) {
                    var node = nodes_1[_i];
                    var name_1 = node.projectName;
                    var status_1 = node.buildStatus;
                    _this.buildStatus.set(name_1, status_1);
                }
                _this.renderGraph();
            })
                .catch(function (error) {
                var msg;
                if (error instanceof Response) {
                    msg = "Error(" + error.status + ") while fetching build data at: " + error.url;
                }
                else {
                    msg = "Error while fetching build data for " + _this.buildUri + "#" + _this.buildNumber;
                }
                _this.graphTitleBar.svg.innerHTML = "<text x='20' y='20'>" + msg + "</text>";
            });
        };
        Graph.prototype.updateBuildStatus = function (projectBuildStatus) {
            var _this = this;
            var _a;
            var jobsBuildStatus = projectBuildStatus.jobBuildStatus;
            var isFinished = projectBuildStatus.finished;
            if (jobsBuildStatus == undefined) {
                throw new Error("'buildStatus' is undefined, the API on the backend has probably changed");
            }
            if (isFinished == undefined) {
                throw new Error("'finished' flag was not received from the api, the API on the backend has probably changed");
            }
            this.graphTitleBar.updateBuildStatus(projectBuildStatus.buildStatus);
            this.graphTitleBar.updateTotalBuildDuration(projectBuildStatus.duration);
            var graphIsMissing = ((_a = this.graph) === null || _a === void 0 ? void 0 : _a.nodes().length) == 0;
            if (graphIsMissing) {
                this.downloadAndRenderGraph()
                    .then(function (voidData) {
                    _this.updateGraphWithNewData(jobsBuildStatus);
                });
            }
            else {
                this.updateGraphWithNewData(jobsBuildStatus);
            }
            return isFinished;
        };
        Graph.prototype.updateGraphWithNewData = function (graphBuildStatus) {
            var _a;
            for (var _i = 0, graphBuildStatus_1 = graphBuildStatus; _i < graphBuildStatus_1.length; _i++) {
                var jobBuildStatus = graphBuildStatus_1[_i];
                var internalBuildStatus = this.buildStatus.get(jobBuildStatus.projectName);
                if (!internalBuildStatus) {
                    console.warn("Build status " + jobBuildStatus.projectName + " was not found in internal status map");
                    continue;
                }
                var statusDidNotChange = internalBuildStatus == jobBuildStatus.buildStatus;
                if (statusDidNotChange) {
                    if (jobBuildStatus.buildStatus != "IN_PROGRESS") {
                        continue;
                    }
                }
                else {
                    this.buildStatus.set(jobBuildStatus.projectName, jobBuildStatus.buildStatus);
                }
                var graphNode = (_a = this.graph) === null || _a === void 0 ? void 0 : _a.node(jobBuildStatus.projectName);
                if (!graphNode) {
                    console.warn("Build node of: " + jobBuildStatus.projectName + " is undefined");
                    continue;
                }
                try {
                    var d3Node = graphNode.elem;
                    d3Node.setAttribute("class", "node " + jobBuildStatus.buildStatus);
                    var buildLinks = d3Node.getElementsByClassName("buildLink");
                    if (!buildLinks) {
                        console.warn("Build link " + jobBuildStatus.projectName + "#" + jobBuildStatus.buildNumber + " was not found in the graph node");
                    }
                    else {
                        for (var i = 0; i < buildLinks.length; i++) {
                            var element = buildLinks.item(i);
                            if (element instanceof HTMLAnchorElement) {
                                var anchor = element;
                                anchor.innerText = jobBuildStatus.buildNumber == -1 ? "#" : "#" + jobBuildStatus.buildNumber;
                                anchor.href = this.baseUri + "/" + jobBuildStatus.buildUri + "/console";
                            }
                            else {
                                console.warn("Found element is not an anchor element");
                            }
                        }
                    }
                    var triggerButtons = d3Node.getElementsByClassName("iconButton");
                    if (!triggerButtons) {
                        console.warn("Trigger button was not found for " + jobBuildStatus.projectName + "#" + jobBuildStatus.buildNumber);
                    }
                    else {
                        for (var i = 0; i < triggerButtons.length; i++) {
                            var buttonElement = triggerButtons.item(i);
                            if (buttonElement instanceof HTMLButtonElement) {
                                if (buttonElement.dataset.status != jobBuildStatus.buildStatus) {
                                    buttonElement.innerHTML = createIcon(jobBuildStatus.buildStatus);
                                    buttonElement.dataset.status = jobBuildStatus.buildStatus;
                                }
                            }
                            else {
                                console.warn("Trigger button was not an element of HTMLButtonElement " + jobBuildStatus.projectName + "#" + jobBuildStatus.buildNumber);
                            }
                        }
                    }
                    var durationField = d3Node.getElementsByClassName("duration");
                    if (!durationField) {
                        console.warn("'duration' element is missing was not found in the graph node " + jobBuildStatus.projectName + "#" + jobBuildStatus.buildNumber);
                    }
                    else {
                        for (var i = 0; i < durationField.length; i++) {
                            var element = durationField.item(i);
                            if (element instanceof HTMLParagraphElement) {
                                element.innerText = jobBuildStatus.duration;
                            }
                        }
                    }
                }
                catch (error) {
                    console.error(error);
                }
            }
        };
        Graph.prototype.fetchAndUpdateBuildStatus = function () {
            var _this = this;
            var uri = this.buildUri + "api/json?tree=status";
            var promise = fetch(uri).then(function (data) {
                if (data.ok) {
                    return data.json();
                }
                throw data;
            })
                .then(function (dataJson) {
                var projectBuildStatus = dataJson['status'];
                if (projectBuildStatus == undefined) {
                    throw new Error("Project build status " + _this.buildUri + " is undefined, the API on the backend has probably changed");
                }
                var isFinished = _this.updateBuildStatus(projectBuildStatus);
                return isFinished;
            })
                .catch(function (error) {
                if (error instanceof Response) {
                    console.error(error.statusText + " " + error.status + ": failed to update builds status of " + _this.buildUri);
                }
                else {
                    console.error("Failed to update build status of " + _this.buildUri + ", " + error);
                }
                return true;
            });
            return promise;
        };
        return Graph;
    }());
    function getWindowLocation() {
        var location = window.location.toString();
        var endsWithSlash = location[location.length - 1] === "/";
        if (endsWithSlash) {
            return location.substr(0, location.length - 1);
        }
        return location;
    }
    function lastBuildChecker(projectName, lastBuild, maxNumberOfGraphs) {
        var updateUrl = getWindowLocation() + "/api/json?tree=lastBuild[number,url]";
        var url = lastBuild.url;
        var arr = url.split("/");
        arr.pop();
        arr.pop();
        var defaultUrl = arr.join("/");
        var previousBuildNumber = lastBuild.number;
        setInterval(function () {
            fetch(updateUrl)
                .then(function (data) {
                if (data.ok) {
                    return data.json();
                }
                throw data;
            })
                .then(function (dataJson) {
                var _a;
                var lastBuildNumber = (_a = dataJson['lastBuild']) === null || _a === void 0 ? void 0 : _a.number;
                if (!lastBuildNumber) {
                    return;
                }
                var buildHasChanged = lastBuildNumber > previousBuildNumber;
                if (buildHasChanged) {
                    var currentBuildUrl = defaultUrl + "/" + lastBuildNumber + "/";
                    var build = {
                        _class: "",
                        url: currentBuildUrl,
                        number: lastBuildNumber
                    };
                    downloadAndRenderBuild(projectName, build, POSITION_SVG.PREPEND, maxNumberOfGraphs);
                    previousBuildNumber = lastBuildNumber;
                }
            })
                .catch(function (error) {
                console.error(error);
            });
        }, LATEST_BUILD_CHECK_MS);
    }
    var POSITION_SVG;
    (function (POSITION_SVG) {
        POSITION_SVG[POSITION_SVG["APPEND"] = 0] = "APPEND";
        POSITION_SVG[POSITION_SVG["PREPEND"] = 1] = "PREPEND";
    })(POSITION_SVG || (POSITION_SVG = {}));
    ;
    function downloadBuildData(buildUrl) {
        var uri = buildUrl + "api/json?tree=dslBuild";
        return fetch(uri);
    }
    function downloadAndRenderBuild(projectName, build, typeOfAdd, maxNumberOfGraphs) {
        var _a;
        if (typeOfAdd === void 0) { typeOfAdd = POSITION_SVG.APPEND; }
        var svgContainer = document.getElementById("svgContainer");
        if (!svgContainer) {
            console.error("SVG container could not be found, this is a bug");
            return;
        }
        var graphDomNode = new GraphTitleBar(projectName, build.number, build.url);
        if (typeOfAdd == POSITION_SVG.APPEND) {
            svgContainer.appendChild(graphDomNode.container);
        }
        else {
            svgContainer.prepend(graphDomNode.container);
            if (svgContainer.childNodes.length > maxNumberOfGraphs) {
                (_a = svgContainer.lastChild) === null || _a === void 0 ? void 0 : _a.remove();
            }
        }
        var graph = new Graph(graphDomNode);
        graph.downloadAndRenderGraph()
            .then(function (voidData) {
            var isFinished = graph.buildData ? graph.buildData.finished : false;
            if (!isFinished) {
                var backgroundUpdater_1 = -1;
                backgroundUpdater_1 = setInterval(function () {
                    var finished = graph.fetchAndUpdateBuildStatus();
                    finished.then(function (taskIsDone) {
                        if (taskIsDone) {
                            clearInterval(backgroundUpdater_1);
                        }
                    }).catch(function (error) {
                        console.error(error);
                        clearInterval(backgroundUpdater_1);
                    });
                }, GRAPH_PROGRESS_UPDATE_MS);
            }
        });
    }
    function fetchAndRenderAllBuilds() {
        var buildsUri = getWindowLocation() + ("/api/json?tree=builds[number,url]{0," + MAX_BUILDS_FETCHED + "},name,numberOfGraphs");
        fetch(buildsUri)
            .then(function (data) {
            if (data.ok) {
                return data.json();
            }
            throw data;
        })
            .then(function (jsonData) {
            var projectName = jsonData['name'] ? jsonData['name'] : "Unknown project";
            var maxNumberOfGraphs = jsonData['numberOfGraphs'] ? jsonData['numberOfGraphs'] : 5;
            var builds = jsonData['builds'];
            if (builds == undefined || builds.length == 0) {
                var noBuild = { _class: "", number: 0, url: getWindowLocation() + "/0/" };
                lastBuildChecker(projectName, noBuild, maxNumberOfGraphs);
                return;
            }
            var maxNumberOfBuilds = Math.min(maxNumberOfGraphs, builds.length);
            for (var i = 0; i < maxNumberOfBuilds; i++) {
                var build = builds[i];
                downloadAndRenderBuild(projectName, build, POSITION_SVG.APPEND, maxNumberOfGraphs);
            }
            var lastBuild = builds[0];
            lastBuildChecker(projectName, lastBuild, maxNumberOfGraphs);
        })
            .catch(function (error) {
            if (error instanceof Response) {
                console.error("Error(" + error.status + ") while fetching graph data for: " + error.url);
            }
            else {
                console.error(error);
            }
        });
    }
    fetchAndRenderAllBuilds();
});
//# sourceMappingURL=displayBuildGraphs.js.map