$(function () {
    const $products = $('#products');
    const $status = $('#status');
    const $count = $('#result-count');
    const $pageLabel = $('#page-label');
    const $pagination = $('.pagination');
    let page = 0;
    let pendingRequest;
    let searchTimer;

    function card(product) {
        const $card = $('<article>', { class: 'product-card' });
        const $visual = $('<div>', { class: 'card-visual' });
        const $monogram = $('<span>', { class: 'product-initial', text: product.name.charAt(0) });
        $visual.append($monogram, $('<span>', { class: 'visual-label', text: product.category.name }));
        const $body = $('<div>', { class: 'card-body' });
        $body.append($('<span>', { class: 'category-label', text: product.category.name }));
        $body.append($('<h3>', { text: product.name }));
        $body.append($('<p>', { text: product.description }));
        const $tags = $('<div>', { class: 'tags' });
        product.keywords.slice(0, 3).forEach(keyword => $tags.append($('<span>', { text: keyword })));
        $body.append($tags);
        return $card.append($visual, $body);
    }

    function showStatus(message) {
        $status.text(message).prop('hidden', false);
    }

    function loadProducts() {
        if (pendingRequest) pendingRequest.abort();
        const [sort, direction] = $('#sort').val().split(',');
        $products.empty();
        $pagination.hide();
        showStatus('Loading products…');
        pendingRequest = $.ajax({
            url: '/api/products',
            data: { q: $('#search').val(), category: $('#category').val(), sort, direction, page, size: 12 },
            dataType: 'json'
        }).done(function (result) {
            $status.prop('hidden', true);
            $count.text(result.totalElements + (result.totalElements === 1 ? ' product' : ' products') + ' found');
            if (result.content.length === 0) {
                showStatus('No products match your search. Try another keyword or category.');
                return;
            }
            result.content.forEach(product => $products.append(card(product)));
            $pageLabel.text('Page ' + (result.number + 1) + ' of ' + result.totalPages);
            $('#previous').prop('disabled', result.first);
            $('#next').prop('disabled', result.last);
            $pagination.show();
        }).fail(function (_xhr, status) {
            if (status !== 'abort') {
                $count.text('Could not load products');
                showStatus('The catalog could not be loaded. Please refresh the page and try again.');
            }
        }).always(function () { pendingRequest = null; });
    }

    window.catalogCategories = $.getJSON('/api/categories').done(function (categories) {
        categories.forEach(category => $('#category').append($('<option>', { value: category.slug, text: category.name })));
    }).fail(function () {
        $('#category').prop('disabled', true);
    });

    $('#search').on('input', function () {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(function () { page = 0; loadProducts(); }, 250);
    });
    $('#category, #sort').on('change', function () { page = 0; loadProducts(); });
    $('#previous').on('click', function () { if (page > 0) { page--; loadProducts(); window.scrollTo({ top: $('.catalog-section').offset().top - 20, behavior: 'smooth' }); } });
    $('#next').on('click', function () { page++; loadProducts(); window.scrollTo({ top: $('.catalog-section').offset().top - 20, behavior: 'smooth' }); });
    $count.text('Ready to explore');
    $pagination.hide();
    showStatus('Choose a category, search for a product, or use the command bar to explore.');
});
