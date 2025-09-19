// Navigation JavaScript - 모든 페이지 공통
console.log('Navigation script loaded!');

document.addEventListener('DOMContentLoaded', function() {
    console.log('DOMContentLoaded fired!');

    // Active link highlighting
    const currentPath = window.location.pathname + window.location.search;
    const menuLinks = document.querySelectorAll('.primary-menu .menu-link, .cat-chip');

    menuLinks.forEach(link => {
        const match = link.getAttribute('data-match') || link.getAttribute('href');
        if (match === '/' && (currentPath === '/' || currentPath === '/?')) {
            link.classList.add('active');
        } else if (match && match !== '/' && currentPath.startsWith(match)) {
            link.classList.add('active');
        }
    });

    // Desktop dropdown hover interactions
    const dropdownItems = document.querySelectorAll('.has-dropdown');
    console.log('Found dropdown items:', dropdownItems.length);

    dropdownItems.forEach((item, index) => {
        const btn = item.querySelector('.dropdown-toggle-btn');
        const panel = item.querySelector('.dropdown-panel');
        let hideTimer;

        console.log(`Dropdown ${index}:`, btn ? 'btn found' : 'btn missing', panel ? 'panel found' : 'panel missing');

        if (!btn || !panel) return;

        const showPanel = () => {
            clearTimeout(hideTimer);
            panel.classList.add('open');
            btn.setAttribute('aria-expanded', 'true');
        };

        const hidePanel = () => {
            panel.classList.remove('open');
            btn.setAttribute('aria-expanded', 'false');
        };

        item.addEventListener('mouseenter', showPanel);
        item.addEventListener('mouseleave', () => {
            hideTimer = setTimeout(hidePanel, 150);
        });

        btn.addEventListener('click', (e) => {
            e.preventDefault();
            panel.classList.toggle('open');
            btn.setAttribute('aria-expanded', panel.classList.contains('open'));
        });
    });

    // Search overlay functionality
    const searchOverlay = document.getElementById('searchOverlay');
    const searchOpenBtn = document.getElementById('searchOpenBtn');
    const searchCloseBtn = document.getElementById('searchCloseBtn');
    const searchBackdrop = document.getElementById('searchBackdrop');

    const openSearch = () => {
        if (searchOverlay) {
            searchOverlay.classList.add('show');
            searchOverlay.setAttribute('aria-hidden', 'false');
            document.documentElement.classList.add('no-scroll');
            setTimeout(() => {
                const searchField = searchOverlay.querySelector('.search-field');
                if (searchField) searchField.focus();
            }, 100);
        }
    };

    const closeSearch = () => {
        if (searchOverlay) {
            searchOverlay.classList.remove('show');
            searchOverlay.setAttribute('aria-hidden', 'true');
            document.documentElement.classList.remove('no-scroll');
        }
    };

    if (searchOpenBtn) searchOpenBtn.addEventListener('click', openSearch);
    if (searchCloseBtn) searchCloseBtn.addEventListener('click', closeSearch);
    if (searchBackdrop) searchBackdrop.addEventListener('click', closeSearch);

    // Close search on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && searchOverlay && searchOverlay.classList.contains('show')) {
            closeSearch();
        }
    });

    // Search suggestions functionality
    document.querySelectorAll('.suggestion-tag').forEach(tag => {
        tag.addEventListener('click', () => {
            const searchField = searchOverlay ? searchOverlay.querySelector('.search-field') : null;
            if (searchField) {
                searchField.value = tag.textContent.trim();
                searchField.focus();
            }
        });
    });

    // Mobile accordion functionality
    document.querySelectorAll('.mobile-accordion').forEach(accordion => {
        accordion.addEventListener('click', () => {
            accordion.classList.toggle('open');
            const panel = accordion.nextElementSibling;
            if (panel) {
                if (panel.style.maxHeight) {
                    panel.style.maxHeight = null;
                } else {
                    panel.style.maxHeight = panel.scrollHeight + 'px';
                }
            }
        });
    });

    // Mobile quick search
    const mobileQuickSearch = document.getElementById('mobileQuickSearch');
    if (mobileQuickSearch) {
        mobileQuickSearch.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const query = e.target.value.trim();
                if (query) {
                    window.location.href = `/search?q=${encodeURIComponent(query)}`;
                }
            }
        });
    }
});