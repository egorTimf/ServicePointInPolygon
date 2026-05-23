// ==================== КОНФИГУРАЦИЯ ====================
const API_URL = '/polygons';
const CANVAS_SIZE = 500;
let loadedPolygons = {};
let editingPolygonId = null;
let editorMode = 'move';
let selectedVertexIndex = null;
let selectedRingIndex = 0;

// ==================== API ====================
async function apiUploadPolygons(geoJson) {
    const res = await fetch(API_URL + '/', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(geoJson)
    });
    if (!res.ok) throw new Error('Ошибка сервера');
    return await res.json();
}

async function apiLoadPolygons() {
    const res = await fetch(API_URL + '/');
    return await res.json();
}

async function apiDeletePolygon(id) {
    await fetch(`${API_URL}/${id}`, { method: 'DELETE' });
}

async function apiCheckPoint(polygonIds, x, y) {
    const res = await fetch(`${API_URL}/check-multiple`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ polygonIds, x, y })
    });
    if (!res.ok) throw new Error('Ошибка проверки');
    return await res.json();
}

async function apiMoveVertex(id, ringIndex, vertexIndex, x, y) {
    const res = await fetch(`${API_URL}/${id}/vertex/move`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ringIndex, vertexIndex, x, y })
    });
    if (!res.ok) throw new Error(await res.text());
}

async function apiAddVertex(id, ringIndex, afterIndex, x, y) {
    const res = await fetch(`${API_URL}/${id}/vertex/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ringIndex, afterIndex, x, y })
    });
    if (!res.ok) throw new Error(await res.text());
}

async function apiRemoveVertex(id, ringIndex, vertexIndex) {
    const res = await fetch(`${API_URL}/${id}/vertex/${ringIndex}/${vertexIndex}`, {
        method: 'DELETE'
    });
    if (!res.ok) throw new Error(await res.text());
}

// ==================== CANVAS ====================
function getCanvasContext() {
    return document.getElementById('viz-canvas').getContext('2d');
}

function transformY(y) { return CANVAS_SIZE - y; }

function drawGrid() {
    const ctx = getCanvasContext();
    const step = 50;
    const styles = getComputedStyle(document.body);

    ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
    ctx.strokeStyle = styles.getPropertyValue('--grid');
    ctx.lineWidth = 0.5;

    for (let i = 0; i <= CANVAS_SIZE; i += step) {
        ctx.beginPath(); ctx.moveTo(i, 0); ctx.lineTo(i, CANVAS_SIZE); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(0, i); ctx.lineTo(CANVAS_SIZE, i); ctx.stroke();
    }

    ctx.strokeStyle = styles.getPropertyValue('--axis');
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(0, CANVAS_SIZE); ctx.lineTo(CANVAS_SIZE, CANVAS_SIZE); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(0, CANVAS_SIZE); ctx.stroke();
}

function drawAllPolygons() {
    drawGrid();
    const ctx = getCanvasContext();

    Object.entries(loadedPolygons).forEach(([id, geo]) => {
        const isEditing = editingPolygonId === parseInt(id);
        drawPolygon(ctx, geo, isEditing);
    });

    // Обновляем подсказку
    updateCanvasHint();
}

function drawPolygon(ctx, geo, highlight = false) {
    if (!geo || geo.type !== 'Polygon') return;

    const rings = geo.coordinates;

    rings.forEach((ring, ringIdx) => {
        ctx.beginPath();
        ring.forEach((pt, i) => {
            const x = pt[0];
            const y = transformY(pt[1]);
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        });
        ctx.closePath();

        ctx.fillStyle = highlight ? 'rgba(13, 110, 253, 0.15)' : 'rgba(13, 110, 253, 0.08)';
        ctx.fill();
        ctx.strokeStyle = highlight ? '#0d6efd' : '#6c757d';
        ctx.lineWidth = highlight ? 2.5 : 1.5;
        ctx.stroke();

        // Вершины
        if (highlight) {
            ring.forEach((pt, i) => {
                const x = pt[0];
                const y = transformY(pt[1]);
                const isSelected = (i === selectedVertexIndex && ringIdx === selectedRingIndex);

                ctx.beginPath();
                ctx.arc(x, y, isSelected ? 6 : 3, 0, 2 * Math.PI);
                ctx.fillStyle = isSelected ? '#ff0000' : '#0d6efd';
                ctx.fill();
                ctx.strokeStyle = '#fff';
                ctx.lineWidth = 1;
                ctx.stroke();

                // Номер вершины
                ctx.font = '9px Consolas';
                ctx.fillStyle = getComputedStyle(document.body).getPropertyValue('--text');
                ctx.fillText(i, x + 6, y - 6);
            });
        }
    });
}

function drawPoint(x, y, label = '') {
    const ctx = getCanvasContext();
    const px = x;
    const py = transformY(y);

    ctx.beginPath();
    ctx.arc(px, py, 5, 0, 2 * Math.PI);
    ctx.fillStyle = 'red';
    ctx.fill();
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    if (label) {
        ctx.font = '11px Consolas';
        ctx.fillStyle = getComputedStyle(document.body).getPropertyValue('--text');
        ctx.fillText(label, px + 8, py - 8);
    }
}

function updateCanvasHint() {
    const hint = document.getElementById('canvas-hint');
    if (editingPolygonId && editorMode === 'add') {
        hint.textContent = 'Кликните на canvas чтобы добавить вершину';
    } else if (editingPolygonId && editorMode === 'move') {
        hint.textContent = 'Кликните на вершину и перетащите (или кликните чтобы выбрать, потом кликните новое место)';
    } else if (editingPolygonId && editorMode === 'delete') {
        hint.textContent = 'Кликните на вершину чтобы удалить';
    } else if (document.querySelectorAll('.poly-chk:checked').length > 0) {
        hint.textContent = 'Кликните по canvas чтобы проверить точку';
    } else {
        hint.textContent = '';
    }
}

// ==================== ОБРАБОТЧИК CANVAS ====================
async function handleCanvasClick(event) {
    const canvas = document.getElementById('viz-canvas');
    const rect = canvas.getBoundingClientRect();
    const scaleX = CANVAS_SIZE / rect.width;
    const scaleY = CANVAS_SIZE / rect.height;

    const canvasX = (event.clientX - rect.left) * scaleX;
    const canvasY = (event.clientY - rect.top) * scaleY;
    const logicalX = canvasX;
    const logicalY = CANVAS_SIZE - canvasY;

    // Если редактируем полигон
    if (editingPolygonId) {
        const geo = loadedPolygons[editingPolygonId];
        if (!geo) return;

        if (editorMode === 'add') {
            // Добавляем вершину
            await apiAddVertex(editingPolygonId, 0, geo.coordinates[0].length - 1, logicalX, logicalY);
            await loadPolygons();
            openEditor(editingPolygonId);
        }
        else if (editorMode === 'delete') {
            // Ищем ближайшую вершину
            const nearest = findNearestVertex(geo, logicalX, logicalY, 10);
            if (nearest) {
                if (geo.coordinates[0].length <= 3) {
                    alert('Полигон должен иметь минимум 3 вершины!');
                    return;
                }
                await apiRemoveVertex(editingPolygonId, 0, nearest.index);
                await loadPolygons();
                openEditor(editingPolygonId);
            }
        }
        else if (editorMode === 'move') {
            const nearest = findNearestVertex(geo, logicalX, logicalY, 10);
            if (nearest) {
                if (selectedVertexIndex === nearest.index && selectedRingIndex === 0) {
                    // Повторный клик — перемещаем
                    await apiMoveVertex(editingPolygonId, 0, nearest.index, logicalX, logicalY);
                    selectedVertexIndex = null;
                    await loadPolygons();
                    openEditor(editingPolygonId);
                } else {
                    // Выбираем вершину
                    selectedVertexIndex = nearest.index;
                    selectedRingIndex = 0;
                    drawAllPolygons();
                    renderVertexList();
                }
            } else if (selectedVertexIndex !== null) {
                // Кликнули в пустое место — перемещаем выбранную вершину
                await apiMoveVertex(editingPolygonId, 0, selectedVertexIndex, logicalX, logicalY);
                selectedVertexIndex = null;
                await loadPolygons();
                openEditor(editingPolygonId);
            }
        }
        return;
    }

    // Проверка точки
    const checkedIds = Array.from(document.querySelectorAll('.poly-chk:checked'))
        .map(cb => parseInt(cb.value));

    if (checkedIds.length > 0) {
        try {
            const results = await apiCheckPoint(checkedIds, logicalX, logicalY);
            drawGrid();
            if (results.length > 0) {
                results.forEach(r => {
                    const geo = loadedPolygons[r.polygonId];
                    if (geo) drawPolygon(getCanvasContext(), geo, true);
                });
                drawPoint(logicalX, logicalY, `[${logicalX.toFixed(1)}, ${logicalY.toFixed(1)}]`);
                const status = results[0].inside ? 'ВНУТРИ' : 'СНАРУЖИ';
                alert(`Точка ${status} полигона ID=${results[0].polygonId}`);
            }
        } catch (e) {
            console.error(e);
        }
    }
}

function findNearestVertex(geo, x, y, threshold) {
    if (!geo || !geo.coordinates) return null;

    let nearest = null;
    let minDist = threshold;

    geo.coordinates.forEach((ring, ringIdx) => {
        ring.forEach((pt, idx) => {
            const dist = Math.sqrt((pt[0] - x) ** 2 + (pt[1] - y) ** 2);
            if (dist < minDist) {
                minDist = dist;
                nearest = { ring: ringIdx, index: idx, dist };
            }
        });
    });

    return nearest;
}

// ==================== UI ====================
function toggleTheme() {
    const body = document.body;
    body.setAttribute('data-theme', body.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');
    drawAllPolygons();
}

function updateEditorUI() {
    document.getElementById('btn-mode-move').classList.toggle('active', editorMode === 'move');
    document.getElementById('btn-mode-add').classList.toggle('active', editorMode === 'add');
    document.getElementById('btn-mode-delete').classList.toggle('active', editorMode === 'delete');
    updateCanvasHint();
    drawAllPolygons();
}

async function uploadPolygons() {
    const status = document.getElementById('upload-status');
    const input = document.getElementById('poly-input').value;
    status.innerText = "⏳ Отправка...";
    try {
        const geoJson = JSON.parse(input);
        const data = await apiUploadPolygons(geoJson);
        status.innerHTML = `<span style="color: var(--success);">✅ ID: ${data.ids.join(', ')}</span>`;
        loadPolygons();
    } catch (e) {
        status.innerText = "❌ " + e.message;
    }
}

async function loadPolygons() {
    const listDiv = document.getElementById('polygons-list');
    try {
        const polygons = await apiLoadPolygons();
        listDiv.innerHTML = '';
        loadedPolygons = {};

        if (!polygons || polygons.length === 0) {
            listDiv.innerHTML = '<i>База данных пуста</i>';
            drawGrid();
            return;
        }

        polygons.forEach(p => {
            try {
                loadedPolygons[p.id] = JSON.parse(p.coordsJson);
            } catch (e) {
                console.error('Ошибка парсинга:', e);
            }
            listDiv.innerHTML += `
                <div class="poly-item">
                    <label style="flex: 1;">
                        <input type="checkbox" class="poly-chk" value="${p.id}" ${editingPolygonId === p.id ? 'checked' : ''}> 
                        ID: ${p.id} (${p.name || 'Без имени'})
                    </label>
                    <div>
                        <button class="btn btn-outline" style="padding: 4px 8px; font-size: 10px;" onclick="openEditor(${p.id})">✏️</button>
                        <button class="btn btn-outline" style="padding: 4px 8px; font-size: 10px;" onclick="deletePolygon(${p.id})">🗑</button>
                    </div>
                </div>`;
        });
        drawAllPolygons();
        if (editingPolygonId) renderVertexList();
    } catch (e) {
        listDiv.innerHTML = `<span style="color:red">Ошибка загрузки</span>`;
    }
}

async function deletePolygon(id) {
    if (!confirm('Удалить полигон #' + id + '?')) return;
    await apiDeletePolygon(id);
    if (editingPolygonId === id) closeEditor();
    loadPolygons();
}

function openEditor(id) {
    editingPolygonId = id;
    selectedVertexIndex = null;
    selectedRingIndex = 0;
    editorMode = 'move';

    document.getElementById('editor-card').style.display = 'block';
    document.getElementById('editor-id').textContent = '#' + id;

    updateEditorUI();
    renderVertexList();
    drawAllPolygons();
}

function closeEditor() {
    editingPolygonId = null;
    selectedVertexIndex = null;
    document.getElementById('editor-card').style.display = 'none';
    updateCanvasHint();
    drawAllPolygons();
}

function renderVertexList() {
    const list = document.getElementById('vertex-list');
    const geo = loadedPolygons[editingPolygonId];
    if (!geo) { list.innerHTML = '<i>Нет данных</i>'; return; }

    let html = '<div style="font-weight: bold; margin-bottom: 4px;">Внешний контур:</div>';
    geo.coordinates[0].forEach((pt, i) => {
        const sel = (i === selectedVertexIndex) ? 'selected' : '';
        html += `
            <div class="vertex-row ${sel}">
                <span style="width: 25px;">${i}</span>
                <span>x: <b>${pt[0].toFixed(2)}</b></span>
                <span>y: <b>${pt[1].toFixed(2)}</b></span>
                <input type="number" step="0.1" value="${pt[0]}" style="width: 60px;" 
                    onchange="quickMoveVertex(${i}, this.value, ${pt[1]})" title="X">
                <input type="number" step="0.1" value="${pt[1]}" style="width: 60px;" 
                    onchange="quickMoveVertex(${i}, ${pt[0]}, this.value)" title="Y">
            </div>`;
    });
    list.innerHTML = html;
}

async function quickMoveVertex(index, x, y) {
    await apiMoveVertex(editingPolygonId, 0, index, parseFloat(x), parseFloat(y));
    selectedVertexIndex = index;
    await loadPolygons();
    openEditor(editingPolygonId);
}

async function saveEdits() {
    document.getElementById('edit-status').innerText = '✅ Сохранено';
    setTimeout(() => document.getElementById('edit-status').innerText = '', 2000);
}

async function checkPoints() {
    const ids = Array.from(document.querySelectorAll('.poly-chk:checked')).map(cb => parseInt(cb.value));
    const pointsStr = document.getElementById('points-input').value;
    const tbody = document.getElementById('results-tbody');

    if (ids.length === 0) { alert("Выберите полигоны!"); return; }

    let points;
    try { points = JSON.parse(pointsStr); }
    catch { alert("Ошибка JSON!"); return; }

    tbody.innerHTML = '';
    for (const pt of points) {
        try {
            const data = await apiCheckPoint(ids, pt[0], pt[1]);
            data.forEach(item => {
                const inText = item.inside
                    ? '<span style="color:var(--success);font-weight:bold;">ВНУТРИ</span>'
                    : '<span style="color:var(--danger);">СНАРУЖИ</span>';
                tbody.innerHTML += `<tr>
                    <td>[${pt[0]}, ${pt[1]}]</td>
                    <td>#${item.polygonId}</td>
                    <td>${inText}</td>
                    <td><button class="btn btn-outline" style="font-size:10px;padding:2px 6px;" 
                        onclick="drawAllPolygons();drawPoint(${pt[0]},${pt[1]},'[${pt[0]},${pt[1]}]')">👁</button></td>
                </tr>`;
            });
        } catch (e) { console.error(e); }
    }
}

// ==================== ИНИЦИАЛИЗАЦИЯ ====================
document.addEventListener('DOMContentLoaded', () => {
    drawGrid();
    loadPolygons();
    document.getElementById('viz-canvas').addEventListener('click', handleCanvasClick);
});