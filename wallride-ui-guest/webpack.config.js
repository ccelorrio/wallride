const webpack = require('webpack');
const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = {
	entry: "./src/app.js",
	output: {
		path: path.resolve(__dirname, "dist"),
		filename: 'resources/guest/bundle.js'
	},
	module: {
		rules: [
			{
				test: /\.css$/,
				use: [MiniCssExtractPlugin.loader, 'css-loader'],
			},
			{
				test: /\.(jpg|png|gif)$/,
				use: 'url-loader',
			},
			{
				test: /\.(ttf|otf|eot|svg|woff2?)(\?v=[0-9]\.[0-9]\.[0-9])?$/,
				loader: 'file-loader',
				options: {
					name: '[path][name].[ext]',
					emitFile: false
				}
			}
		]
	},
	plugins: [
		new webpack.ProvidePlugin({
			$: "jquery",
			jQuery: "jquery",
			"window.jQuery": "jquery",
			"global.jQuery": "jquery"
		}),
		new MiniCssExtractPlugin({ filename: 'resources/guest/bundle.css'}),
		new CopyWebpackPlugin({
			patterns : [
				{ from: 'node_modules/bootstrap/dist/fonts/*', to: 'resources/guest' },
				{ context: 'src/resources', from: 'img/**/*', to: 'resources/guest' },
				{ context: 'src/templates', from: '**/*', to: 'templates/guest' }
			]
		})
	],
	devServer: {
		contentBase: [
			path.resolve(__dirname, "src/templates"),
			path.resolve(__dirname, "src/resources")
		],
		port: 8000
	}
};