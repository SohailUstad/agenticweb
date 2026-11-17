$(function () {
    const $form = $('#command-form');
    const $input = $('#command-input');
    const $feedback = $('#command-feedback');
    const dock = document.querySelector('.command-dock');

    const categoryAliases = {
        phones: 'smartphones', phone: 'smartphones',
        computers: 'laptops', computer: 'laptops',
        shoes: 'footwear', shoe: 'footwear',
        coffee: 'coffee-gear', games: 'video-games',
        bikes: 'bicycles', bike: 'bicycles',
        pets: 'pet-supplies', pet: 'pet-supplies'
    };

    function reserveSpace() {
        document.documentElement.style.setProperty('--command-dock-height', dock.offsetHeight + 'px');
    }
    reserveSpace();
    if (window.ResizeObserver) new ResizeObserver(reserveSpace).observe(dock);
    window.addEventListener('resize', reserveSpace);

    function hasPhrase(text, phrase) {
        return (' ' + text + ' ').includes(' ' + phrase + ' ');
    }

    function removePhrase(text, phrase) {
        return (' ' + text + ' ').replace(' ' + phrase + ' ', ' ').trim();
    }

    function showResults() {
        window.scrollTo({ top: $('.catalog-section').offset().top - 20, behavior: 'smooth' });
    }

    function applyCommand(raw, categories) {
        const command = raw.toLowerCase().replace(/[?!.,]/g, ' ').replace(/\s+/g, ' ').trim();
        if (!command) return;

        if (/^(reset|clear filters|show all products|list all products|all products)$/.test(command)) {
            $('#category').val('');
            $('#search').val('');
            $('#sort').val('name,asc').trigger('change');
            $feedback.text('Showing all products.');
            showResults();
            return;
        }
        if (/^(next|next page)$/.test(command)) {
            if (!$('#next').prop('disabled') && $('.pagination').is(':visible')) $('#next').trigger('click');
            return;
        }
        if (/^(previous|previous page|prev|prev page)$/.test(command)) {
            if (!$('#previous').prop('disabled') && $('.pagination').is(':visible')) $('#previous').trigger('click');
            return;
        }

        const sortField = /\b(?:sort|sorted|order|ordered)\s+(?:by\s+)?categor(?:y|ies)\b/.test(command) ? 'category' : 'name';
        const direction = /\b(?:z\s*(?:to|-)\s*a|descending|reverse)\b/.test(command) ? 'desc' : 'asc';
        let remaining = command
            .replace(/\b(?:sort|sorted|order|ordered)\s+(?:by\s+)?(?:name|categor(?:y|ies))\b/g, ' ')
            .replace(/\b(?:a\s*(?:to|-)\s*z|z\s*(?:to|-)\s*a|ascending|descending|reverse|alphabetically)\b/g, ' ')
            .replace(/\s+/g, ' ').trim();

        const category = [...categories].sort((a, b) => b.name.length - a.name.length)
            .find(item => hasPhrase(remaining, item.name.toLowerCase()));
        let slug = category ? category.slug : '';
        if (category) remaining = removePhrase(remaining, category.name.toLowerCase());
        if (!slug) {
            for (const [alias, aliasSlug] of Object.entries(categoryAliases)) {
                if (hasPhrase(remaining, alias)) {
                    slug = aliasSlug;
                    remaining = removePhrase(remaining, alias);
                    break;
                }
            }
        }

        const query = remaining
            .replace(/\b(?:show|list|find|search|display|give|me|please|products?|items?|all|the|in|from|for|with|that|are|only|of|a|an|some|and|by|sort|sorted|order|ordered|category)\b/g, ' ')
            .replace(/\s+/g, ' ').trim().slice(0, 100);

        $('#category').val(slug);
        $('#search').val(query);
        $('#sort').val(sortField + ',' + direction).trigger('change');
        $feedback.text('Showing catalog results for your command.');
        showResults();
    }

    $form.on('submit', function (event) {
        event.preventDefault();
        const command = $input.val().trim();
        if (!command) return;
        window.catalogCategories.done(categories => applyCommand(command, categories))
            .fail(() => applyCommand(command, []));
    });
});
