$(function () {
    const $form = $('#command-form');
    const $input = $('#command-input');
    const $feedback = $('#command-feedback');
    const $submitButton = $form.find('button[type="submit"]');
    const dock = document.querySelector('.command-dock');

    function reserveSpace() {
        document.documentElement.style.setProperty('--command-dock-height', dock.offsetHeight + 'px');
    }
    reserveSpace();
    if (window.ResizeObserver) new ResizeObserver(reserveSpace).observe(dock);
    window.addEventListener('resize', reserveSpace);

    function showResults() {
        window.scrollTo({ top: $('.catalog-section').offset().top - 20, behavior: 'smooth' });
    }

    function setCommandBusy(isBusy) {
        $submitButton.prop('disabled', isBusy).toggleClass('is-loading', isBusy).attr('aria-busy', isBusy);
        $input.prop('disabled', isBusy);
    }

    function applyIntent(intent, parameters) {
        const values = parameters || {};

        switch (intent) {
            case 'list_products':
                $('#category').val('');
                $('#search').val('');
                $('#sort').val('name,asc').trigger('change');
                $feedback.text('Showing all products.');
                showResults();
                return;

            case 'filter_by_category':
                const categoryOptionExists = $('#category option').toArray()
                    .some(function (option) { return option.value === values.categorySlug; });
                if (!values.categorySlug || !categoryOptionExists) {
                    $feedback.text('That category is not available.');
                    return;
                }
                $('#category').val(values.categorySlug).trigger('change');
                $feedback.text('Showing the selected category.');
                showResults();
                return;

            case 'search_products':
                if (typeof values.keywordQuery !== 'string' || !values.keywordQuery.trim()) return;
                $('#search').val(values.keywordQuery.trim().slice(0, 100)).trigger('input');
                $feedback.text('Searching products.');
                showResults();
                return;

            case 'sort_products':
                if (!['name', 'category'].includes(values.sortField)
                    || !['asc', 'desc'].includes(values.sortDirection)) return;
                $('#sort').val(values.sortField + ',' + values.sortDirection).trigger('change');
                $feedback.text('Sorting products.');
                showResults();
                return;

            case 'next_page':
                if (values.pageDirection === 'next' && !$('#next').prop('disabled')) {
                    $('#next').trigger('click');
                }
                return;

            case 'previous_page':
                if (values.pageDirection === 'previous' && !$('#previous').prop('disabled')) {
                    $('#previous').trigger('click');
                }
                return;

            case 'reset_filters':
                $('#category').val('');
                $('#search').val('');
                $('#sort').val('name,asc').trigger('change');
                $feedback.text('Filters cleared.');
                showResults();
                return;

            case 'general_ai_question':
                $feedback.text('AI response logged in the browser console.');
                return;

            case 'unknown':
            default:
                $feedback.text('I could not identify a catalog action for that request.');
        }
    }

    function applyAiCommand(prompt) {
        return $.ajax({
            url: '/api/ai/prompt',
            method: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            data: JSON.stringify({ prompt })
        }).done(function (result) {
            if (result.response) console.log('AI response:', result.response);
            applyIntent(result.intent, result.parameters || {});
        }).fail(function (xhr) {
            $feedback.text('The AI command could not be processed.');
            console.error('AI command failed:', xhr.responseJSON || xhr.statusText);
        });
    }

    $form.on('submit', function (event) {
        event.preventDefault();
        const command = $input.val().trim();
        if (!command) return;
        setCommandBusy(true);
        applyAiCommand(command).always(function () {
            setCommandBusy(false);
        });
    });
});
