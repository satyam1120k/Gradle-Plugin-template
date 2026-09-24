package com.guard.report

object HtmlScripts {
    fun getScriptBody(): String {
        return """
        const rawNodes = dataset.nodes || [];
        const rawEdges = dataset.edges || [];

        document.getElementById('node-count').innerText = rawNodes.length;
        document.getElementById('edge-count').innerText = rawEdges.length;

        const tableBody = document.getElementById('tableBody');
        rawNodes.forEach(n => {
            const row = document.createElement('tr');
            const actionsStr = (n.obfuscationPlan || []).join(', ');
            const color = getNodeColorHex(n.riskCategory);
            row.innerHTML = `
                <td>${'$'}{n.className}</td>
                <td><b>${'$'}{n.methodName}</b></td>
                <td><span style="color:${'$'}{color}; font-weight:bold;">${'$'}{n.riskCategory}</span></td>
                <td>${'$'}{n.riskScore}</td>
                <td style="color:#4ec9b0;">${'$'}{actionsStr}</td>
            `;
            row.style.cursor = 'pointer';
            row.onclick = () => showInspector(n);
            tableBody.appendChild(row);
        });

        function getNodeColorHex(cat) {
            if (cat === 'Critical') return '#f44336';
            if (cat === 'High') return '#ff9800';
            if (cat === 'Medium') return '#ffeb3b';
            return '#2196F3';
        }

        function switchView(viewName, evt) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.view-panel').forEach(p => { p.classList.remove('active'); p.style.display = 'none'; });

            evt.target.classList.add('active');
            if (viewName === 'graph') {
                const p = document.getElementById('view-graph');
                p.classList.add('active'); p.style.display = 'block';
            } else if (viewName === 'matrix') {
                const p = document.getElementById('view-matrix');
                p.classList.add('active'); p.style.display = 'block';
                drawMatrix();
            } else if (viewName === 'table') {
                const p = document.getElementById('view-table');
                p.classList.add('active'); p.style.display = 'block';
            }
        }

        // Draggable Overlay Logic for Risk Matrix
        const matrixOverlay = document.getElementById('matrix-overlay');
        let isDraggingBox = false;
        let startX = 0, startY = 0;

        matrixOverlay.addEventListener('mousedown', (e) => {
            isDraggingBox = true;
            startX = e.clientX - matrixOverlay.offsetLeft;
            startY = e.clientY - matrixOverlay.offsetTop;
            e.stopPropagation();
        });

        window.addEventListener('mousemove', (e) => {
            if (!isDraggingBox) return;
            matrixOverlay.style.left = (e.clientX - startX) + 'px';
            matrixOverlay.style.top = (e.clientY - startY) + 'px';
            matrixOverlay.style.bottom = 'auto';
        });

        window.addEventListener('mouseup', () => {
            isDraggingBox = false;
        });

        const canvas = document.getElementById('graphCanvas');
        const ctx = canvas.getContext('2d');

        function resizeCanvas() {
            canvas.width = canvas.parentElement.clientWidth || 800;
            canvas.height = canvas.parentElement.clientHeight || 600;
        }
        window.addEventListener('resize', resizeCanvas);
        resizeCanvas();

        let nodes = rawNodes.map((n, i) => {
            const angle = (i / rawNodes.length) * 2 * Math.PI;
            const dist = 120 + (i % 3) * 50;
            return {
                ...n,
                x: (canvas.width / 2) + Math.cos(angle) * dist,
                y: (canvas.height / 2) + Math.sin(angle) * dist,
                vx: 0, vy: 0, radius: 16
            };
        });

        const nodeMap = new Map(nodes.map(n => [n.id, n]));
        let edges = rawEdges.map(e => ({
            source: nodeMap.get(e.source),
            target: nodeMap.get(e.target)
        })).filter(e => e.source && e.target);

        let selectedNode = null;
        let hoveredNode = null;
        let draggedNode = null;

        function simulatePhysics() {
            const repulsion = 800;
            const springLength = 100;
            const springConstant = 0.04;
            const cx = canvas.width / 2;
            const cy = canvas.height / 2;

            for (let i = 0; i < nodes.length; i++) {
                for (let j = i + 1; j < nodes.length; j++) {
                    const dx = nodes[j].x - nodes[i].x;
                    const dy = nodes[j].y - nodes[i].y;
                    let dist = Math.hypot(dx, dy) || 1;
                    if (dist < 120) {
                        let force = repulsion / (dist * dist);
                        const fx = (dx / dist) * force;
                        const fy = (dy / dist) * force;
                        if (nodes[i] !== draggedNode) { nodes[i].vx -= fx; nodes[i].vy -= fy; }
                        if (nodes[j] !== draggedNode) { nodes[j].vx += fx; nodes[j].vy += fy; }
                    }
                }
            }

            edges.forEach(e => {
                const dx = e.target.x - e.source.x;
                const dy = e.target.y - e.source.y;
                const dist = Math.hypot(dx, dy) || 1;
                const force = (dist - springLength) * springConstant;
                const fx = (dx / dist) * force;
                const fy = (dy / dist) * force;
                if (e.source !== draggedNode) { e.source.vx += fx; e.source.vy += fy; }
                if (e.target !== draggedNode) { e.target.targetId ? null : (e.target.vx -= fx, e.target.vy -= fy); }
            });

            nodes.forEach(n => {
                if (n === draggedNode) return;
                n.vx += (cx - n.x) * 0.005;
                n.vy += (cy - n.y) * 0.005;
                n.vx *= 0.75; n.vy *= 0.75;
                n.x += n.vx; n.y += n.vy;
                n.x = Math.max(n.radius, Math.min(canvas.width - n.radius, n.x));
                n.y = Math.max(n.radius, Math.min(canvas.height - n.radius, n.y));
            });
        }

        function drawGraph() {
            simulatePhysics();
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            ctx.lineWidth = 1;
            edges.forEach(e => {
                const isHL = (e.source === selectedNode || e.target === selectedNode || e.source === hoveredNode || e.target === hoveredNode);
                ctx.strokeStyle = isHL ? '#4ec9b0' : '#333';
                ctx.beginPath();
                ctx.moveTo(e.source.x, e.source.y);
                ctx.lineTo(e.target.x, e.target.y);
                ctx.stroke();
            });

            nodes.forEach(n => {
                ctx.beginPath();
                ctx.arc(n.x, n.y, n.radius, 0, 2 * Math.PI);
                ctx.fillStyle = getNodeColorHex(n.riskCategory);
                ctx.fill();
                ctx.lineWidth = (n === selectedNode || n === hoveredNode) ? 3 : 1;
                ctx.strokeStyle = '#ffffff';
                ctx.stroke();

                ctx.fillStyle = '#cccccc';
                ctx.font = '10px sans-serif';
                ctx.textAlign = 'center';
                let disp = (n.methodName || 'unknown').replace('<', '').replace('>', '');
                if (disp.length > 14) disp = disp.substring(0, 12) + '...';
                ctx.fillText(disp, n.x, n.y + 26);
            });

            requestAnimationFrame(drawGraph);
        }

        const mCanvas = document.getElementById('matrixCanvas');
        mCanvas.width = mCanvas.parentElement.clientWidth || 800;
        mCanvas.height = mCanvas.parentElement.clientHeight || 600;
        const mCtx = mCanvas.getContext('2d');

        function drawMatrix() {
            mCanvas.width = mCanvas.parentElement.clientWidth || 800;
            mCanvas.height = mCanvas.parentElement.clientHeight || 600;
            mCtx.clearRect(0, 0, mCanvas.width, mCanvas.height);

            const pad = 60;
            const w = mCanvas.width - (pad * 2);
            const h = mCanvas.height - (pad * 2);

            mCtx.strokeStyle = '#444';
            mCtx.lineWidth = 1;
            mCtx.beginPath();
            mCtx.moveTo(pad, pad); mCtx.lineTo(pad, pad + h); mCtx.lineTo(pad + w, pad + h);
            mCtx.stroke();

            mCtx.fillStyle = '#aaa';
            mCtx.font = '12px sans-serif';
            mCtx.fillText('PageRank (Global Importance ➔)', pad + (w / 2) - 60, pad + h + 40);
            mCtx.save();
            mCtx.translate(pad - 40, pad + (h / 2) + 40);
            mCtx.rotate(-Math.PI / 2);
            mCtx.fillText('Risk Score (Threat Level ➔)', 0, 0);
            mCtx.restore();

            if (nodes.length === 0) return;

            let minPR = Infinity, maxPR = -Infinity;
            let minRS = Infinity, maxRS = -Infinity;

            nodes.forEach(n => {
                if (n.pageRank < minPR) minPR = n.pageRank;
                if (n.pageRank > maxPR) maxPR = n.pageRank;
                if (n.riskScore < minRS) minRS = n.riskScore;
                if (n.riskScore > maxRS) maxRS = n.riskScore;
            });

            if (maxPR === minPR) { maxPR += 1.0; minPR -= 1.0; }
            if (maxRS === minRS) { maxRS += 1.0; minRS -= 1.0; }

            nodes.forEach(n => {
                const normPR = (n.pageRank - minPR) / (maxPR - minPR);
                const normRS = (n.riskScore - minRS) / (maxRS - minRS);

                const px = pad + (normPR * w);
                const py = pad + h - (normRS * h);

                mCtx.beginPath();
                mCtx.arc(px, py, n === selectedNode ? 9 : 6, 0, 2 * Math.PI);
                mCtx.fillStyle = getNodeColorHex(n.riskCategory);
                mCtx.fill();
                mCtx.lineWidth = n === selectedNode ? 2 : 1;
                mCtx.strokeStyle = '#fff';
                mCtx.stroke();
            });
        }

        canvas.addEventListener('mousedown', (e) => {
            const rect = canvas.getBoundingClientRect();
            const mx = e.clientX - rect.left; const my = e.clientY - rect.top;
            nodes.forEach(n => {
                if (Math.hypot(n.x - mx, n.y - my) < n.radius + 5) {
                    draggedNode = n; selectedNode = n; showInspector(n);
                }
            });
        });

        mCanvas.addEventListener('click', (e) => {
            const rect = mCanvas.getBoundingClientRect();
            const mx = e.clientX - rect.left; const my = e.clientY - rect.top;
            const pad = 60; const w = mCanvas.width - (pad * 2); const h = mCanvas.height - (pad * 2);

            if (nodes.length === 0) return;

            let minPR = Infinity, maxPR = -Infinity;
            let minRS = Infinity, maxRS = -Infinity;

            nodes.forEach(n => {
                if (n.pageRank < minPR) minPR = n.pageRank;
                if (n.pageRank > maxPR) maxPR = n.pageRank;
                if (n.riskScore < minRS) minRS = n.riskScore;
                if (n.riskScore > maxRS) maxRS = n.riskScore;
            });

            if (maxPR === minPR) { maxPR += 1.0; minPR -= 1.0; }
            if (maxRS === minRS) { maxRS += 1.0; minRS -= 1.0; }

            nodes.forEach(n => {
                const normPR = (n.pageRank - minPR) / (maxPR - minPR);
                const normRS = (n.riskScore - minRS) / (maxRS - minRS);

                const px = pad + (normPR * w);
                const py = pad + h - (normRS * h);
                if (Math.hypot(px - mx, py - my) < 12) {
                    selectedNode = n; showInspector(n); drawMatrix();
                }
            });
        });

        canvas.addEventListener('mousemove', (e) => {
            const rect = canvas.getBoundingClientRect();
            const mx = e.clientX - rect.left; const my = e.clientY - rect.top;
            if (draggedNode) { draggedNode.x = mx; draggedNode.y = my; draggedNode.vx = 0; draggedNode.vy = 0; }
            hoveredNode = null;
            nodes.forEach(n => { if (Math.hypot(n.x - mx, n.y - my) < n.radius) hoveredNode = n; });
        });

        window.addEventListener('mouseup', () => { draggedNode = null; });

        function showInspector(node) {
            const detailsDiv = document.getElementById('details');
            const actionsList = (node.obfuscationPlan || []).map(a => `<li>${'$'}{a}</li>`).join('');
            detailsDiv.innerHTML = `
                <h3 style="color: #4ec9b0; margin-top:0;">${'$'}{node.className}</h3>
                <p style="color: #ce9178; margin: 4px 0;"><b>Method:</b> ${'$'}{node.methodName}</p>
                <p style="margin: 4px 0;"><b>Risk Tier:</b> <span style="color: ${'$'}{getNodeColorHex(node.riskCategory)}; font-weight:bold;">${'$'}{node.riskCategory} (Score: ${'$'}{node.riskScore})</span></p>
                <p style="margin: 4px 0;"><b>Assigned Actions:</b></p>
                <ul style="margin: 0; padding-left: 18px; color: #4ec9b0;">${'$'}{actionsList}</ul>
                <hr style="border-color: #444; margin: 10px 0;">
                <div class="metric-row"><span class="metric-name">Crypto API Count</span><span class="metric-val">${'$'}{node.cryptoApiCount}</span></div>
                <div class="metric-row"><span class="metric-name">Network API Count</span><span class="metric-val">${'$'}{node.networkApiCount}</span></div>
                <div class="metric-row"><span class="metric-name">File API Count</span><span class="metric-val">${'$'}{node.fileApiCount}</span></div>
                <div class="metric-row"><span class="metric-name">Reflection Used</span><span class="metric-val">${'$'}{node.reflectionUsed}</span></div>
                <div class="metric-row"><span class="metric-name">Sensitive Data Flow</span><span class="metric-val">${'$'}{node.sensitiveDataFlow}</span></div>
                <div class="metric-row"><span class="metric-name">PageRank</span><span class="metric-val">${'$'}{node.pageRank.toFixed(4)}</span></div>
                <div class="metric-row"><span class="metric-name">Fan-In</span><span class="metric-val">${'$'}{node.fanIn}</span></div>
                <div class="metric-row"><span class="metric-name">Fan-Out</span><span class="metric-val">${'$'}{node.fanOut}</span></div>
                <div class="metric-row"><span class="metric-name">Loop Count</span><span class="metric-val">${'$'}{node.loopCount}</span></div>
                <div class="metric-row"><span class="metric-name">Branch Count</span><span class="metric-val">${'$'}{node.branchCount}</span></div>
                <div class="metric-row"><span class="metric-name">Permission Score</span><span class="metric-val">${'$'}{node.permissionScore}</span></div>
            `;
        }

        requestAnimationFrame(drawGraph);
        """.trimIndent()
    }
}