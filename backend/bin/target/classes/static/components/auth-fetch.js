// Attaches "Authorization: Bearer <token>" to same-origin fetch() calls automatically.
// The backend moved from session-cookie auth to stateless JWT, but these pages were
// built assuming the browser would send auth on its own — this patches that gap
// without having to touch every individual fetch() call site.
(function () {
    const originalFetch = window.fetch;

    window.fetch = function (input, init) {
        const token = sessionStorage.getItem('token') || localStorage.getItem('token');
        const url = typeof input === 'string' ? input : input.url;
        const isSameOrigin = url.startsWith('/') || url.startsWith(window.location.origin);

        if (token && isSameOrigin) {
            init = init || {};
            const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
            if (!headers.has('Authorization')) {
                headers.set('Authorization', 'Bearer ' + token);
            }
            init.headers = headers;
        }

        return originalFetch.call(this, input, init);
    };
})();
