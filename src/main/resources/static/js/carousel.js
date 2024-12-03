document.addEventListener('DOMContentLoaded', function() {
    const carouselSlide = document.querySelector('.logo-carousel-slide');
    const images = document.querySelectorAll('.logo-carousel-slide img');
    const dotsContainer = document.querySelector('.logo-carousel-dots');

    let counter = 0;
    const size = images[0].clientWidth;

    carouselSlide.style.transform = 'translateX(' + (-size * counter) + 'px)';

    images.forEach((_, idx) => {
        const dot = document.createElement('div');
        dot.classList.add('logo-dot');
        if (idx === 0) dot.classList.add('active');
        dot.addEventListener('click', () => {
            counter = idx;
            updateCarousel();
        });
        dotsContainer.appendChild(dot);
    });

    document.querySelector('#nextBtn').addEventListener('click', () => {
        if (counter >= images.length - 1) return;
        counter++;
        updateCarousel();
    });

    document.querySelector('#prevBtn').addEventListener('click', () => {
        if (counter <= 0) return;
        counter--;
        updateCarousel();
    });

    function autoSlide() {
        if (counter >= images.length - 1) {
            counter = 0;
        } else {
            counter++;
        }
        updateCarousel();
    }

    function updateCarousel() {
        carouselSlide.style.transform = 'translateX(' + (-size * counter) + 'px)';
        document.querySelectorAll('.logo-dot').forEach((dot, idx) => {
            dot.classList.toggle('active', idx === counter);
        });
    }

    let slideInterval = setInterval(autoSlide, 5000);

    const carousel = document.querySelector('.logo-carousel-container');
    carousel.addEventListener('mouseenter', () => {
        clearInterval(slideInterval);
    });

    carousel.addEventListener('mouseleave', () => {
        slideInterval = setInterval(autoSlide, 5000);
    });
});