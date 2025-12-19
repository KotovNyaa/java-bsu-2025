const API_URL = 'http://localhost:8080/api';

async function loadData() {
    try {
        const res = await fetch(`${API_URL}/spy/all`);
        const data = await res.json();
        const tbody = document.querySelector('#anime-table tbody');
        
        data.forEach(anime => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${anime.id}</td>
                <td>${anime.title}</td>
                <td>${anime.membersCount.toLocaleString()}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    alert("SYSTEM ALERT: YOUR IP HAS BEEN LOGGED.");
    loadData();
});