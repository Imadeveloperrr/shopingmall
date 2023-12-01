document.addEventListener('DOMContentLoaded', function () {
    var accessToken = localStorage.getItem('accessToken');
    document.getElementById('navMyPage').style.display = accessToken ? 'block' : 'none';
    document.getElementById('navLogin').style.display = accessToken ? 'none' : 'block';
    document.getElementById('navRegister').style.display = accessToken ? 'none' : 'block';
    if (accessToken) {
        console.log('accessToken have')
    } else {
        console.log('no accessToken')
    }
})