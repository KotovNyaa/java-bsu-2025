const API_URL = 'http://localhost:8080/api';

let currentLeft = null;
let currentRight = null;
let lastWinner = null; 
let streak = 0;
let isAnimating = false;

const bestStreaks = JSON.parse(localStorage.getItem('anime_best_streaks')) || {
    easy: 0, medium: 0, hard: 0, extreme: 0, impossible: 0
};

document.addEventListener('DOMContentLoaded', () => {
    updateDifficultyUI();
    loadNextRound(true);
});

document.getElementById('difficulty-select').addEventListener('change', () => {
    streak = 0;
    updateStreakUI();
    updateDifficultyUI();
    loadNextRound(true);
});

function getDifficulty() {
    return document.getElementById('difficulty-select').value;
}

function updateDifficultyUI() {
    document.getElementById('best-streak').textContent = bestStreaks[getDifficulty()];
}

function updateStreakUI() {
    document.getElementById('current-streak').textContent = streak;
}

// Функция для формирования пути
function getImagePath(rawPath) {
    if (!rawPath) return '';
    if (rawPath.startsWith('http')) return rawPath;
    let cleanPath = rawPath.replace(/['"]/g, '');
    if (cleanPath.includes('anime_posters')) return cleanPath;
    return `anime_posters/${cleanPath}`;
}

// === НОВАЯ ФУНКЦИЯ: ОБРАБОТЧИК ОШИБКИ ===
// Если картинка не загрузилась, ставим заглушку
function handleImageError(imgElement) {
    // Чтобы не зациклилось, проверяем, не стоит ли уже заглушка
    if (!imgElement.src.includes('placehold.co')) {
        imgElement.src = 'https://placehold.co/400x600/313244/cdd6f4?text=No+Image';
    }
}

async function loadNextRound(isReset = false) {
    try {
        const diff = getDifficulty();
        const res = await fetch(`${API_URL}/game/next?difficulty=${diff}`);
        const pair = await res.json();

        if (isReset) {
            currentLeft = pair[0];
            currentRight = pair[1];
        } else {
            if (lastWinner) {
                currentLeft = lastWinner;
                currentRight = (pair[0].id === currentLeft.id) ? pair[1] : pair[0];
            } else {
                currentLeft = pair[0];
                currentRight = pair[1];
            }
        }
        render();
    } catch (e) {
        console.error(e);
    }
}

function render() {
    const leftImg = document.getElementById('img-left');
    const rightImg = document.getElementById('img-right');

    // Назначаем обработчик ошибки ПЕРЕД установкой src
    leftImg.onerror = () => handleImageError(leftImg);
    rightImg.onerror = () => handleImageError(rightImg);

    document.getElementById('title-left').textContent = currentLeft.title;
    leftImg.src = getImagePath(currentLeft.imagePath);
    document.getElementById('members-left').textContent = currentLeft.membersCount.toLocaleString();

    document.getElementById('title-right').textContent = currentRight.title;
    rightImg.src = getImagePath(currentRight.imagePath);
    
    // UI Reset
    const rightMembersEl = document.getElementById('members-right');
    const rightLabelEl = document.getElementById('label-right');
    
    rightMembersEl.textContent = "?";
    rightMembersEl.className = "question-mark";
    rightMembersEl.style.color = "var(--blue)";
    rightLabelEl.style.display = "none";
    
    document.querySelectorAll('.card').forEach(c => c.style.borderColor = 'transparent');
    isAnimating = false;
}

async function handleGuess(side) {
    if (isAnimating) return;
    isAnimating = true;

    const selected = (side === 'left') ? currentLeft : currentRight;
    const other = (side === 'left') ? currentRight : currentLeft;

    try {
        const res = await fetch(`${API_URL}/game/check`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                selectedId: selected.id,
                otherId: other.id
            })
        });

        const result = await res.json();

        const rightCount = (side === 'right') ? result.selectedMembers : result.otherMembers;
        currentRight.membersCount = rightCount;

        const rightMembersEl = document.getElementById('members-right');
        const rightLabelEl = document.getElementById('label-right');
        
        rightMembersEl.textContent = rightCount.toLocaleString();
        rightMembersEl.className = "count-number";
        rightLabelEl.style.display = "block";

        if (result.correct) {
            showNotification("Correct!", false);
            document.querySelector(`.card-${side}`).style.borderColor = 'var(--green)';
            streak++;
            updateStreakUI();
            
            lastWinner = currentRight;
            setTimeout(() => { loadNextRound(false); }, 1200);

        } else {
            showNotification("Wrong!", true);
            document.querySelector(`.card-${side}`).style.borderColor = 'var(--red)';
            if (side === 'left') {
                 document.querySelector('.card-right').style.borderColor = 'var(--green)';
            }
            const diff = getDifficulty();
            if (streak > bestStreaks[diff]) {
                bestStreaks[diff] = streak;
                localStorage.setItem('anime_best_streaks', JSON.stringify(bestStreaks));
                updateDifficultyUI();
            }
            setTimeout(() => {
                document.getElementById('final-score').textContent = streak;
                document.getElementById('final-best').textContent = bestStreaks[diff];
                openModal('game-over');
            }, 1200);
        }
    } catch (e) {
        console.error(e);
        isAnimating = false;
    }
}

function showNotification(text, isError) {
    const notif = document.getElementById('notification-area');
    notif.textContent = text;
    notif.className = `notification show ${isError ? 'error' : ''}`;
    setTimeout(() => { notif.className = 'notification'; }, 1000);
}

function restartGame() {
    closeModal('game-over');
    streak = 0;
    updateStreakUI();
    loadNextRound(true);
}

window.openModal = (id) => document.getElementById(`modal-${id}`).classList.add('active');
window.closeModal = (id) => document.getElementById(`modal-${id}`).classList.remove('active');