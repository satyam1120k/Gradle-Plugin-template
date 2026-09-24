package com.guard.report

object HtmlTemplate {
    fun build(jsonString: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>IntelliGuard - AI Adaptive Protection Intelligence</title>
            <style>
                html, body { height: 100%; margin: 0; padding: 0; background: #1e1e1e; color: #fff; font-family: Arial, sans-serif; overflow: hidden; }
                #app { display: flex; flex-direction: column; height: 100vh; width: 100vw; }
                
                #toolbar { height: 50px; background: #2d2d2d; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; border-bottom: 2px solid #333; }
                .tab-group { display: flex; gap: 10px; }
                .tab-btn { background: #3c3c3c; color: #ccc; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-weight: bold; font-size: 13px; }
                .tab-btn.active { background: #4ec9b0; color: #1e1e1e; }
                .stats-info { font-size: 13px; color: #9cdcfe; }

                #content-area { display: flex; flex: 1; height: calc(100vh - 50px); position: relative; }
                
                .view-panel { display: none; width: 70%; height: 100%; position: relative; background: #121212; }
                .view-panel.active { display: block; }
                canvas { display: block; width: 100%; height: 100%; }

                #sidebar { width: 30%; height: 100%; padding: 20px; box-sizing: border-box; background: #252526; overflow-y: auto; border-left: 2px solid #333; }
                h2 { color: #4ec9b0; border-bottom: 2px solid #4ec9b0; padding-bottom: 5px; margin-top: 0; }
                .metric-row { display: flex; justify-content: space-between; padding: 5px 0; border-bottom: 1px solid #3c3c3c; font-size: 13px; }
                .metric-name { color: #9cdcfe; }
                .metric-val { color: #b5cea8; font-weight: bold; }
                .placeholder { color: #888; font-style: italic; margin-top: 30px; text-align: center; }

                #view-table { padding: 30px; overflow-y: auto; width: 70%; background: #141414; box-sizing: border-box; }
                table { width: 100%; border-collapse: collapse; font-size: 13px; }
                th, td { padding: 10px 12px; border: 1px solid #333; text-align: left; }
                th { background: #252526; color: #4ec9b0; }
                tr:hover { background: rgba(78, 201, 176, 0.05); }

                .overlay-box { position: absolute; top: 15px; left: 15px; background: rgba(0,0,0,0.85); padding: 10px 15px; border-radius: 6px; font-size: 12px; color: #ccc; border: 1px solid #444; z-index: 10; pointer-events: none; }
                .legend-item { display: flex; align-items: center; margin-bottom: 4px; pointer-events: auto; }
                .legend-color { width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; display: inline-block; }

                /* Draggable Matrix Overlay */
                #matrix-overlay { cursor: move; pointer-events: auto; user-select: none; }
            </style>
        </head>
        <body>
            <div id="app">
                <div id="toolbar">
                    <div class="tab-group">
                        <button class="tab-btn active" onclick="switchView('graph', event)">1. Force Call Graph</button>
                        <button class="tab-btn" onclick="switchView('matrix', event)">2. Risk Matrix Scatter</button>
                        <button class="tab-btn" onclick="switchView('table', event)">3. Executive Summary Table</button>
                    </div>
                    <div class="stats-info">Nodes: <span id="node-count" style="color:#4ec9b0;font-weight:bold;">0</span> | Edges: <span id="edge-count" style="color:#4ec9b0;font-weight:bold;">0</span></div>
                </div>

                <div id="content-area">
                    <div id="view-graph" class="view-panel active">
                        <div class="overlay-box">
                            <b>Risk Legend</b>
                            <div class="legend-item"><span class="legend-color" style="background:#f44336;"></span>Critical Risk</div>
                            <div class="legend-item"><span class="legend-color" style="background:#ff9800;"></span>High Risk</div>
                            <div class="legend-item"><span class="legend-color" style="background:#ffeb3b;"></span>Medium Risk</div>
                            <div class="legend-item"><span class="legend-color" style="background:#2196F3;"></span>Low Risk</div>
                        </div>
                        <canvas id="graphCanvas"></canvas>
                    </div>

                    <div id="view-matrix" class="view-panel">
                        <div id="matrix-overlay" class="overlay-box">
                            <b>Risk Matrix (Importance vs Risk) ⇕</b><br>
                            <small><i>Click & drag this box anywhere</i><br>X-Axis: PageRank (Global Connectivity)<br>Y-Axis: Risk Score (Assessed Threat)</small>
                        </div>
                        <canvas id="matrixCanvas"></canvas>
                    </div>

                    <div id="view-table" class="view-panel">
                        <h2 style="color:#4ec9b0; margin-top:0;">Adaptive Protection Plan Matrix</h2>
                        <table id="summaryTable">
                            <thead>
                                <tr>
                                    <th>Class Name</th>
                                    <th>Method Name</th>
                                    <th>Risk Tier</th>
                                    <th>Score</th>
                                    <th>Assigned Obfuscation Actions</th>
                                </tr>
                            </thead>
                            <tbody id="tableBody"></tbody>
                        </table>
                    </div>

                    <div id="sidebar">
                        <h2>Parameter Inspector</h2>
                        <div id="details" class="placeholder">Click any method in the graph or matrix to inspect its features and AI protection plan.</div>
                    </div>
                </div>
            </div>

            <script>
                const dataset = $jsonString;
                ${HtmlScripts.getScriptBody()}
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}